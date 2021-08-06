package io.scalac.githubrank
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.PathMatcher
import io.grpc.netty.shaded.io.netty.util.internal.ThreadLocalRandom
import spray.json.{JsArray, JsNumber, JsObject, JsString}

import scala.concurrent.Future

object FakeGitgubAPIConsumer extends GithubAPIConsumer {

  val rand = ThreadLocalRandom.current()

  val pageSize = 2
  val numOfRepos = 2//rand.nextInt(1, 6)

  val maxNumRepoPages = if(numOfRepos % pageSize > 0) numOfRepos/pageSize + 1 else numOfRepos/pageSize

  val repos = (0 until numOfRepos).map { i =>
    JsObject("name" -> JsString("fakeorg"), "full_name" -> JsString(s"repo-$i"))
  }

  val contributers = repos.map { fullName =>
    fullName.fields("full_name").asInstanceOf[JsString].value -> (0 until rand.nextInt(1, 10)).map { j =>
      JsObject("login" -> JsString(s"user-$j"), "contributions" -> JsNumber(rand.nextInt(1, 1000)))
    }
  }.toMap

  val contribsByUser = contributers.values.flatten.map{obj => obj.fields("login").asInstanceOf[JsString].value -> obj.fields("contributions")
    .asInstanceOf[JsNumber].value.toInt}.toSeq.sortBy(_._2).reverse

  implicit val system = ActorSystem.create[Nothing](Behaviors.empty[Nothing], "DefaultGithubAPIConsumer")
  import system.executionContext

  val logger = system.log

  val EMPTY_RESPONSE = Future.successful {
    HttpResponse(
      StatusCodes.OK,
      Seq.empty[HttpHeader],
      HttpEntity(ContentTypes.`application/json`, JsArray.empty.toString())
    )
  }

  override def request(req: HttpRequest): Future[HttpResponse] = {

    val isRepo = req.uri.path.endsWith("repos")
    val page = req.uri.queryString().get.split("&").map { pair =>
      val parts = pair.split("=")
      parts(0) -> parts(1)
    }.toMap.get("page").get.toInt

    if(isRepo){
      if(page > maxNumRepoPages){
        return EMPTY_RESPONSE
      }

      return Future.successful {
        HttpResponse(
          StatusCodes.OK,
          Seq.empty[HttpHeader],
          HttpEntity(ContentTypes.`application/json`, JsArray.apply(repos: _*).toString())
        )
      }
    }

    val repo = req.uri.path.toString().split("/")(2).trim
    val numContribs = contributers("repo-0").length

    val maxNumContribs = if(numContribs % pageSize > 0) numContribs/pageSize + 1 else numContribs/pageSize

    if(page > maxNumContribs){
      return EMPTY_RESPONSE
    }

    Future.successful {
      HttpResponse(
        StatusCodes.OK,
        Seq.empty[HttpHeader],
        HttpEntity.apply(ContentTypes.`application/json`, JsArray(contributers.head._2: _*).toString())
      )
    }
  }

  override def close(): Future[Unit] = {
    /*system.terminate()
    system.whenTerminated.map(_ => {})*/

    Future.successful {}
  }
}
