package io.scalac.githubrank

import spray.json.JsValue
import scala.concurrent.Future

trait GithubAPIService {
  def getContributors(org: String): Future[JsValue]
  def close(): Future[Unit]
}
