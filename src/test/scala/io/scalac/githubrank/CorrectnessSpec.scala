package io.scalac.githubrank

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.testkit.TestDuration
import akka.util.ByteString
import io.scalac.githubrank.impl.DefaultGithubAPIService
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory
import spray.json.JsArray
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration.DurationInt

/**
 * This test mocks GitHub api to asses the correctness of the solution implemented
 */
class CorrectnessSpec extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  val logger = LoggerFactory.getLogger(this.getClass)

  implicit val timeout = RouteTestTimeout(5.minutes.dilated)

  "it " should "match response from mocked API" in {

    implicit val consumer = FakeGitgubAPIConsumer
    implicit val service = new DefaultGithubAPIService(FakeGitgubAPIConsumer.pageSize)
    val server = new Server(port = 8080)

    //server.run()

    val routes = server.routes

    Get("/org/fakeorg/contributors") ~> Route.seal(routes) ~> check {

      status shouldEqual StatusCodes.OK
      contentType shouldEqual ContentTypes.`application/json`

      Unmarshal(response)
        .to[ByteString].map(_.utf8String.parseJson.convertTo[JsArray]).map { result =>

        val elements = result.elements

        logger.info(s"${Console.BLUE_B}result from server: ${result.toJson.prettyPrint}${Console.RESET}")
        logger.info(s"${Console.RED_B}result must be: ${result.toJson.prettyPrint}${Console.RESET}")

        assert(FakeGitgubAPIConsumer.contribsByUser.size == elements.size)

        for(i<-0 until elements.length){
          val obj1 = elements(i).asJsObject.fields
          val (user, n) = FakeGitgubAPIConsumer.contribsByUser(i)

          assert(user.compareTo(obj1("login").asInstanceOf[JsString].value) == 0)
          assert(n == obj1("contributions").asInstanceOf[JsNumber].value.toInt)
        }

      }
    }

    server.close()

  }

}
