package io.scalac.githubrank

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import scala.concurrent.Future

trait GithubAPIConsumer {
  def request(req: HttpRequest): Future[HttpResponse]
  def close(): Future[Unit]
}
