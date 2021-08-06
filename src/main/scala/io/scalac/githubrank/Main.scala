package io.scalac.githubrank

import io.scalac.githubrank.impl.{DefaultGithubAPIConsumer, DefaultGithubAPIService}
import scala.language.postfixOps

object Main {

  def main(args: Array[String]): Unit = {

    implicit val consumer = new DefaultGithubAPIConsumer()
    implicit val service = new DefaultGithubAPIService(pageSize = 100)
    val server = new Server(port = 8080)

    server.run()

  }

}
