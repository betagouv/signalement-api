package controllers

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.http.HeaderNames
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Results
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import repositories.ipblacklist.BlackListedIp
import utils.AppSpec
import utils.TestApp

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class IpBlackListFilterSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  val ipBlackListRepository = components.ipBlackListRepository

  override def setupData(): Unit =
    Await.result(
      for {
        _ <- ipBlackListRepository.create(BlackListedIp("1.2.3.4", "local test", critical = true))
        _ <- ipBlackListRepository.create(BlackListedIp("1.2.5.0/24", "local test", critical = false))
      } yield (),
      Duration.Inf
    )

  "IpBlackListFilter" should {
    "filter ip if in blacklist" in {
      val request = FakeRequest(
        method = GET,
        uri = routes.ConstantController.getCountries().toString,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsEmpty,
        remoteAddress = "1.2.3.4"
      )
      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(403)
    }

    "not filter ips not in the blacklist" in {
      val request = FakeRequest(
        method = GET,
        uri = routes.ConstantController.getCountries().toString,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsEmpty,
        remoteAddress = "10.20.30.40"
      )
      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(200)
    }

    "filter ip if in subnet blacklist" in {
      val request = FakeRequest(
        method = GET,
        uri = routes.ConstantController.getCountries().toString,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsEmpty,
        remoteAddress = "1.2.5.127"
      )
      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(403)
    }

    "not filter ips not in the subnet blacklist" in {
      val request = FakeRequest(
        method = GET,
        uri = routes.ConstantController.getCountries().toString,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsEmpty,
        remoteAddress = "1.2.3.5"
      )
      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(200)
    }
  }
}
