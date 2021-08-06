package io.scalac.githubrank

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn
import scala.language.postfixOps

class Server(val port: Int = 8080)(implicit val service: GithubAPIService){

  implicit val system = ActorSystem.create[Nothing](Behaviors.empty[Nothing], "GithubRank")
  import system.executionContext

  val routes = get {
    path("org" / Segment / "contributors") { org: String =>
      complete {
        service.getContributors(org).map { json =>
          HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/json`), json.compactPrint))
        }
      }
    }
  }

  val bindingFuture = Http().newServerAt("localhost", port).bind(routes)

  system.log.info(s"${Console.GREEN_B}Server online at http://localhost:$port${Console.RESET}\n")

  def run(): Unit = {

    println(s"\n${Console.MAGENTA_B}PRESS ANY KEY TO STOP THE SERVER!${Console.RESET}\n")

    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete { _ =>
        service.close().onComplete {
          case _ => system.terminate()
        }
      }
  }

  def close(): Unit = {
    bindingFuture.onComplete {
      case _ => system.terminate()
    }
  }

}
