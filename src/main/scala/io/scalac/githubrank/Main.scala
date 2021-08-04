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
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials, RawHeader}
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
    val AuthHeader = Authorization(GenericHttpCredentials("token", "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"))

    implicit val system = ActorSystem.create[Nothing](Behaviors.empty[Nothing], "GitHubClient")
    implicit val ec = system.executionContext

    case class Repository(name: String, full_name: String)
    case class Contribution(login: String, contributions: Int)

    implicit val repositoryFormat = jsonFormat2(Repository)
    implicit val contributorFormat = jsonFormat2(Contribution)

    val http = Http()

    def getContributorsByRepo(repo: Repository, page: Int): Future[Seq[Contribution]] = {
      val request =
        Get(s"https://api.github.com/repos/${repo.full_name}/contributors?page=$page&per_page=50")
          .withHeaders(Seq(
            GitHubHeader
          ))

      println(s"checking contributions for repo ${repo}")

      http.singleRequest(request).flatMap(response => Unmarshal(response.entity).to[ByteString]
        .map(_.utf8String.parseJson.convertTo[Seq[Contribution]]))
    }

    def getAllContributorsByRepo(repo: Repository, page: Int = 1): Future[Seq[Contribution]] = {
      getContributorsByRepo(repo, page).flatMap {
        case list if list.isEmpty => Future.successful(list)
        case list => getAllContributorsByRepo(repo, page + 1).map(list ++ _)
      }
    }

    def getReposByOrg(org: String, page: Int): Future[Seq[Repository]] = {
      val request =
        Get(s"https://api.github.com/orgs/$org/repos?page=$page&per_page=50")
          .withHeaders(Seq(
            GitHubHeader,
            AuthHeader
          ))

      http.singleRequest(request).flatMap(response => Unmarshal(response.entity).to[ByteString].map(_.utf8String.parseJson.convertTo[Seq[Repository]]))
    }

    def getAllReposByOrg(org: String, page: Int = 1): Future[Seq[Repository]] = {
      getReposByOrg(org, page).flatMap {
        case list if list.isEmpty => Future.successful(list)
        case list => getAllReposByOrg(org, page + 1).map(list ++ _)
      }
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

    getAllReposByOrg("ScalaConsultants").onComplete {
      case Success(repos) =>

        //logger.info(s"response: ${json.prettyPrint}\n")

        Future.sequence(repos.map(r => getAllContributorsByRepo(r).map(r -> _))).onComplete {
          case Success(list) =>

            println(list)

            system.terminate()

          case Failure(ex) => throw ex
        }

      case Failure(ex) => logger.error(ex.getMessage)
    }

    Await.ready(system.whenTerminated, Duration.Inf)

  }

}
