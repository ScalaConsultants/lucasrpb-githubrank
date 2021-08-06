package io.scalac.githubrank.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import io.scalac.githubrank.GithubAPIConsumer

import scala.concurrent.Future

class DefaultGithubAPIConsumer extends GithubAPIConsumer {

  implicit val system = ActorSystem.create[Nothing](Behaviors.empty[Nothing], "DefaultGithubAPIConsumer")
  import system.executionContext

  override def request(req: HttpRequest): Future[HttpResponse] = {
    Http().singleRequest(req)
  }

  override def close(): Future[Unit] = {
    system.terminate()
    system.whenTerminated.map(_ => {})
  }
}
