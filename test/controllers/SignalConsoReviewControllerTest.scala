package controllers

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.AppSpec
import utils.TestApp

import java.util.UUID
class SignalConsoReviewControllerTest(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp(
    None
  )

  "save signal conso review" in {

    val uniqueComment = UUID.randomUUID().toString

    val jsonString =
      s"""{
         |  "evaluation": 4,
         |  "details": "${uniqueComment}",
         |  "creationDate": "2023-06-01T12:00:00Z",
         |  "platform": "Android"
         |}
         |""".stripMargin
    val jsonBody = Json.parse(jsonString)

    val request = FakeRequest(GET, routes.SignalConsoReviewController.signalConsoReview().toString)
      .withJsonBody(jsonBody)

    val result = for {
      res          <- route(app, request).get
      savedReviews <- components.signalConsoReviewRepository.list()
    } yield (res, savedReviews.find(_.details.contains(uniqueComment)))

    status(result.map(_._1)) must beEqualTo(NO_CONTENT)
    result.map(_._2.flatMap(_.details)) must beSome(uniqueComment).await
  }

}
