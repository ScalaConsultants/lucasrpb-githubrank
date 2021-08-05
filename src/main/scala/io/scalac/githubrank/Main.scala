package io.scalac.githubrank

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import org.slf4j.LoggerFactory
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, ResponseEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.model.HttpProtocols._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{Authorization, ETag, EntityTag, GenericHttpCredentials, RawHeader}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import spray.json._
import DefaultJsonProtocol._
import akka.{Done, NotUsed}
import akka.stream.contrib.PagedSource

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object Main {

  /*sealed trait Command

  case class Get(url: String, sender: ActorRef[ResponseEntity]) extends Command

  def rateLimitedAPIConsumer(): Behavior[Command] = Behaviors.setup { ctx =>

    Behaviors.receive {
      case _ => Behaviors.same
    }
  }*/

  def main(args: Array[String]): Unit = {

    val logger = LoggerFactory.getLogger(this.getClass)

    implicit val actorSystem = ActorSystem.create[Nothing](Behaviors.empty[Nothing], "alpakka-samples")
    import actorSystem.executionContext

    val pageSize        = 100

    /*val httpRequest = HttpRequest(uri = "https://api.github.com/orgs/ScalaConsultants/repos")
      .withHeaders(RawHeader("Accept", "application/vnd.github.v3+json"),
        Authorization(GenericHttpCredentials("token", "")))*/

    val GITHUB_HEADER = RawHeader("Accept", "application/vnd.github.v3+json")
    val AUTH_HEADER = Authorization(GenericHttpCredentials("token", scala.sys.env("GH_TOKEN")))

    case class HttpResponseException(code: StatusCode) extends Throwable
    case class UnmarshalResponseException(msg: String) extends Throwable
    case class HttpConnectionException(msg: String) extends Throwable

    case class MyURI(url: String, page: Int)

    case class Repository(name: String, full_name: String)
    case class Contribution(login: String, contributions: Int)

    implicit val repositoryFormat = jsonFormat2(Repository)
    implicit val contributorFormat = jsonFormat2(Contribution)
    implicit val timeout = Timeout(5 seconds)

    def getPagedSource(url: MyURI) = {
      PagedSource[JsArray, MyURI](url){ case nextPageUri =>

        println(s"${Console.GREEN_B}PROCESSING NEXT PAGE ${nextPageUri}${Console.RESET}")

        Http()
          .singleRequest(HttpRequest(uri = s"${nextPageUri.url}?page=${nextPageUri.page}&per_page=$pageSize")
            .withHeaders(GITHUB_HEADER, AUTH_HEADER))
          .flatMap {
            case httpResponse if httpResponse.status != StatusCodes.OK =>
              //throw HttpResponseException(httpResponse.status)
              Future.successful(PagedSource.Page(
                Seq.empty[JsArray],
                  Some(MyURI(url.url, nextPageUri.page + 1)
                )))

            case httpResponse =>
              Unmarshal(httpResponse)
                .to[ByteString].map(_.utf8String.parseJson.convertTo[JsArray])
                .map { response =>
                  PagedSource.Page(
                    Seq(response),
                    if (response.elements.isEmpty) None
                    else Some(MyURI(url.url, nextPageUri.page + 1))
                  )
                }
                .recover {
                  case ex =>
                    throw UnmarshalResponseException(ex.getMessage)
                }
          }
          .recover {
            case ex: HttpResponseException      => throw ex
            case ex: UnmarshalResponseException => throw ex
            case ex                             => throw HttpConnectionException(ex.getMessage)
          }
      }
    }

    val org = "spray"
    val repos = getPagedSource(MyURI(s"https://api.github.com/orgs/${org}/repos", page = 1))

    def lookupContributors(repos: Seq[Repository]) = {
      Source(repos)
        .flatMapConcat(repo => getPagedSource(MyURI(s"https://api.github.com/repos/${repo.full_name}/contributors", page = 1))
          .map(_.convertTo[Seq[Contribution]]))
    }

    val future =
      repos.map(_.convertTo[Seq[Repository]])
        .flatMapMerge(10, x => lookupContributors(x))
      .throttle(5000, 1 hour)
      .runWith(Sink.seq[Seq[Contribution]])

    future.onComplete {
      case Success(value) =>

        // Flatten all contributions grabbed, group by user and then sort in reversing order
        val contributions = value.flatten.groupBy(_.login).map{case (user, list) => user -> list.map(_.contributions).sum}.toSeq.sortBy(_._2).reverse

        println(contributions)

        actorSystem.terminate()

      case Failure(ex) =>
        //ex.printStackTrace()
        actorSystem.terminate()
    }

  }

}
