package orchestrators.socialmedia

import cats.implicits.catsSyntaxApplicativeId
import models.report.SocialNetworkSlug
import play.api.Logger
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.HttpURLConnectionBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SocialBladeClient()(implicit ec: ExecutionContext) {

  val logger = Logger(this.getClass)
  AsyncHttpClientFutureBackend()

  def checkSocialNetworkUsername(
      platform: SocialNetworkSlug,
      username: String
  ): Future[Option[String]] = {

    val request = basicRequest
      .get(uri"https://socialblade.com/youtube/user/carolinereceveur")
      .header(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"
      )

    val backend  = HttpURLConnectionBackend()
    val response = request.send(backend)

//    val lowercaseUsername = username.toLowerCase()
//    val url               = uri"https://socialblade.com/${platform.entryName.toLowerCase}/user/$lowercaseUsername"
//    val request = emptyRequest
//      .headers(
//        Header.userAgent(
//          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"
//        )
//      )
//      .get(url)
//      .response(asString)

    println(s"------------------ request.toCurl = ${request.toCurl} ------------------")
//
//    request
//      .send(backend)
//      .flatMap { response =>
//        if (response.code.isSuccess) {
//          response.body match {
//            case Right(product) =>
//              logger.debug(s"Call success")
//              Future.successful(Option.when(findUserName(product, lowercaseUsername))(lowercaseUsername))
//            case Left(error) =>
//              logger.warnWithTitle("social_blade_error", s"Error while calling Socialblade: ${error}")
//              Future.successful(None)
//          }
//        } else {
//          logger.warnWithTitle("social_blade_error", s"Error while calling Socialblade: ${response.code}")
//          Future.successful(None)
//        }
//      }

    println(response)

    response.body.toOption.pure[Future]
  }

}
