package io.scalac.githubrank

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import org.slf4j.LoggerFactory
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpProtocols._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object Main {

  def main(args: Array[String]): Unit = {

    val logger = LoggerFactory.getLogger(this.getClass)

    val GitHubHeader = RawHeader("Accept", "application/vnd.github.v3+json")

    implicit val system = ActorSystem.create[Nothing](Behaviors.empty[Nothing], "GitHubClient")
    implicit val ec = system.executionContext

    val http = Http()

    def getReposByOrg(org: String): Future[String] = {
      val request =
        Get(s"https://api.github.com/orgs/$org/repos")
        .withHeaders(Seq(
          GitHubHeader
        ))

      http.singleRequest(request).flatMap(response => Unmarshal(response.entity).to[ByteString].map(_.utf8String.parseJson.prettyPrint))
    }

    def getContributorsByRepo(org: String, repo: String): Future[String] = {
      val request =
        Get(s"https://api.github.com/repos/$org/$repo/contributors")
          .withHeaders(Seq(
            GitHubHeader
          ))

      http.singleRequest(request).flatMap(response => Unmarshal(response.entity).to[ByteString].map(_.utf8String.parseJson.prettyPrint))
    }

    /*(for {
        response <- Http().singleRequest(request)
        entity <- Unmarshal(response.entity).to[ByteString].map(_.utf8String.parseJson)
      } yield entity).onComplete {
      case Success(json) =>

        //logger.info(s"response: ${json.prettyPrint}\n")

        println(json.prettyPrint)

        system.terminate()

      case Failure(ex) => logger.error(ex.getMessage)
    }*/

    Future.sequence(Seq(
      //getReposByOrg("ScalaConsultants"),
      getContributorsByRepo("ScalaConsultants","docker-postgres")
    )).onComplete {
      case Success(data) =>

        //logger.info(s"response: ${json.prettyPrint}\n")

        println(data)

        system.terminate()

      case Failure(ex) => logger.error(ex.getMessage)
    }

    Await.ready(system.whenTerminated, Duration.Inf)

  }

}
