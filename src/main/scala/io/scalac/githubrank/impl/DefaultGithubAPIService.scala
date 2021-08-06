package io.scalac.githubrank.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials, RawHeader}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.contrib.PagedSource
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.google.common.base.Charsets
import io.scalac.githubrank.{GithubAPIService, GithubAPIConsumer, HttpCache}
import io.scalac.githubrank.grpc.CacheItem
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.io.StdIn
import scala.language.postfixOps
import scala.concurrent.Future

class DefaultGithubAPIService(val pageSize: Int)(implicit val apiConsumer: GithubAPIConsumer) extends GithubAPIService {

  implicit val system = ActorSystem.create[Nothing](Behaviors.empty[Nothing], "GithubRankService")
  import system.executionContext

  val GH_TOKEN = scala.sys.env("GH_TOKEN")

  if(GH_TOKEN == null || GH_TOKEN.isEmpty || GH_TOKEN.isBlank){
    throw new RuntimeException(s"GH_TOKEN environment variable must be set!")
  }

  // Http Headers for
  val GITHUB_HEADER = RawHeader("Accept", "application/vnd.github.v3+json")
  val AUTH_HEADER = Authorization(GenericHttpCredentials("token", GH_TOKEN))

  case class HttpResponseException(code: StatusCode) extends Throwable
  case class UnmarshalResponseException(msg: String) extends Throwable
  case class HttpConnectionException(msg: String) extends Throwable

  case class MyURI(url: String, page: Int)

  case class Repository(name: String, full_name: String)
  case class Contribution(login: String, contributions: Int)

  implicit val repositoryFormat = jsonFormat2(Repository)
  implicit val contributorFormat = jsonFormat2(Contribution)
  implicit val timeout = Timeout(5 seconds)

  val cache = new HttpCache(1000)

  val logger = system.log

  /**
   * This function checks the etag cache mechanism to reduce the cost of calls to GitHub api. If the response status from that
   * API is 304 (Not Modified), the content is fetched from the local cache
   *
   * * @param response The http response from the request
   * @param url The next page URL being processed
   * @param cacheItem The cached item if any
   * @return returns a Source Graph
   */
  def checkETag(response: HttpResponse, url: MyURI, cacheItem: Option[CacheItem]): Future[PagedSource.Page[JsArray, MyURI]] = {
    response.status match {
      case s if s == StatusCodes.OK || s == StatusCodes.NotModified =>

        val optEtag = response.getHeader("ETag")
        val optLastModified = response.getHeader("Last-Modified")

        if(s == StatusCodes.NotModified){
          logger.info(s"${Console.MAGENTA_B} etag for ${url}: $optEtag with status ${response.status}${Console.RESET}")
        }

        if(optEtag.isEmpty){
          Unmarshal(response)
            .to[ByteString].map(_.utf8String.parseJson.convertTo[JsArray])
            .map { response =>
              PagedSource.Page(
                Seq(response),
                if (response.elements.isEmpty) None
                else Some(MyURI(url.url, url.page + 1))
              )
            }
            .recover {
              case ex =>
                throw UnmarshalResponseException(ex.getMessage)
            }
        } else if(response.status == StatusCodes.NotModified) {

          val entity = ByteString(cacheItem.get.entity.toByteArray).utf8String.parseJson.convertTo[JsArray]

          Future.successful(
            PagedSource.Page(
              Seq(entity),
              if (entity.elements.isEmpty) None
              else Some(MyURI(url.url, url.page + 1))
            )
          )
        } else {

          val tag = optEtag.get().value()
          Unmarshal(response.entity).to[ByteString].map { bs =>
            val entity = bs.utf8String.parseJson.convertTo[JsArray]

            cache.put(s"${url.url}?page=${url.page}&per_page=${pageSize}", CacheItem(tag, if(optLastModified.isPresent) optLastModified.get().value() else "",
              com.google.protobuf.ByteString.copyFrom(bs.utf8String.getBytes(Charsets.UTF_8))))

            PagedSource.Page(
              Seq(entity),
              if (entity.elements.isEmpty) None
              else Some(MyURI(url.url, url.page + 1))
            )
          }
        }

      case _ =>
        Future.successful(
          PagedSource.Page(
            Seq.empty[JsArray],
            Some(MyURI(url.url, url.page + 1)
            ))
        )
    }
  }

  /**
   * Generates a paged source that fetches pages from the GitHub API supporting backpressure to not consume all the request
   * quota of 5000 req/s
   * @param url
   * @return
   */
  def getPagedSource(url: MyURI) = {
    PagedSource[JsArray, MyURI](url){ case nextPageUri =>

      logger.info(s"${Console.GREEN_B}PROCESSING NEXT PAGE ${nextPageUri}${Console.RESET}")

      val uri = s"${nextPageUri.url}?page=${nextPageUri.page}&per_page=${pageSize}"
      val cacheItem = cache.get(uri)

      var headers = Seq(GITHUB_HEADER, AUTH_HEADER)

      if(cacheItem.isDefined){
        headers = headers ++ Seq(RawHeader("If-None-Match", cacheItem.get.tag), RawHeader("If-Modified-Since", cacheItem.get.lastModified))
        logger.info(s"${RawHeader("If-None-Match", cacheItem.get.tag)}\n")
      }

      val request = HttpRequest(uri = uri).withHeaders(headers)

      apiConsumer.request(request)
        .flatMap { response =>
          checkETag(response, nextPageUri, cacheItem)
        }
        .recover {
          case ex: HttpResponseException      => throw ex
          case ex: UnmarshalResponseException => throw ex
          case ex                             => throw HttpConnectionException(ex.getMessage)
        }
    }
  }

  def lookupContributors(repos: Seq[Repository]) = {
    Source(repos)
      .flatMapConcat(repo => getPagedSource(MyURI(s"https://api.github.com/repos/${repo.full_name}/contributors", page = 1))
        .map(_.convertTo[Seq[Contribution]]))
  }

  def getContributors(org: String): Future[JsValue] = {
    val repos = getPagedSource(MyURI(s"https://api.github.com/orgs/${org}/repos", page = 1))

    val graph =
      repos.map(_.convertTo[Seq[Repository]])
        .flatMapMerge(10, x => lookupContributors(x))
        // Backpressure...
        .throttle(5000, 1 hour)

    graph.runWith(Sink.seq[Seq[Contribution]]).map { value =>
      // Flatten all contributions grabbed, group by user and then sort in reversing order
      val contributions = value.flatten.groupBy(_.login).map{case (user, list) => user -> list.map(_.contributions).sum}.toSeq.sortBy(_._2).reverse

      JsArray.apply(
        contributions.map { case (user, n) =>
          JsObject(
            "name" -> JsString(user),
            "contributions" -> JsNumber(n)
          )
        }: _*
      )
    }
  }

  override def close(): Future[Unit] = {
    system.terminate()

    for {
      _ <- apiConsumer.close()
      _ <- Future {cache.close()}
    } yield {
      system.whenTerminated.map(_ => {})
    }
  }
}
