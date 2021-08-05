package io.scalac.githubrank

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.model.ResponseEntity
import akka.util.Timeout

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

object HttpCache {

  sealed trait Command
  protected case class GetItem(url: String, sender: ActorRef[Option[(String, String, ResponseEntity)]]) extends Command
  protected case class PutItem(url: String, data: (String, String, ResponseEntity), sender: ActorRef[Boolean]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val cache = TrieMap.empty[String, (String, String, ResponseEntity)]

    Behaviors.receiveMessage {
      case GetItem(url, sender) =>
        sender ! cache.get(url)
        Behaviors.same

      case PutItem(url, data, sender) =>
        cache.put(url, data)
        sender ! true
        Behaviors.same

      case _ => Behaviors.same
    }
  }

  val system = ActorSystem.create[Command](apply(), "GithubCache")
  implicit val sc = system.scheduler
  implicit val ec = system.executionContext

  def get(url: String)(implicit timeout: Timeout): Future[Option[(String, String, ResponseEntity)]] = {
    system.ask[Option[(String, String, ResponseEntity)]] { sender =>
      GetItem(url, sender)
    }
  }

  def put(url: String, data: (String, String, ResponseEntity))(implicit timeout: Timeout): Future[Boolean] = {
    system.ask[Boolean] { sender =>
      PutItem(url, data, sender)
    }
  }

}
