package orchestrators.socialmedia

import config.SocialBladeClientConfiguration
import models.report.SocialNetworkSlug
import models.report.SocialNetworkSlug.Facebook
import models.report.SocialNetworkSlug.Instagram
import models.report.SocialNetworkSlug.TikTok
import models.report.SocialNetworkSlug.Twitch
import models.report.SocialNetworkSlug.Twitter
import models.report.SocialNetworkSlug.YouTube
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.Identity
import sttp.client3.RequestT
import sttp.client3.Response
import sttp.client3.UriContext
import sttp.client3.asString
import sttp.client3.basicRequest
import sttp.model.StatusCode.PaymentRequired
import utils.Logs.RichLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

class SocialBladeClient(config: SocialBladeClientConfiguration)(implicit ec: ExecutionContext) {

  val logger  = Logger(this.getClass)
  val backend = AsyncHttpClientFutureBackend()

  def checkSocialNetworkUsername(
      platform: SocialNetworkSlug,
      username: String
  ): Future[Option[CertifiedInflencerResponse]] = {

    val request: RequestT[Identity, Either[String, String], Any] = basicRequest
      .get(uri"${config.url}/b/${platform.entryName.toLowerCase}/statistics?query=${username}")
      .header("clientid", config.clientId)
      .header("token", config.token)
      .header("history", "default")
      .response(asString)

    logger.infoWithTitle("socialblade_client", request.toCurl(Set("clientid", "token")))

    request.send(backend).map {
      case Response(Right(body), statusCode, _, _, _, _) if statusCode.isSuccess =>
        handleSuccessResponse(body, username, platform)

      case Response(Right(body), PaymentRequired, _, _, _, _) =>
        logger.warnWithTitle(
          "socialblade_client_error",
          s"No credit left for SocialBlade : $body"
        )
        // Act as the username does not exist in social blade
        None

      case Response(Right(body), statusCode, _, _, _, _) =>
        logger.errorWithTitle(
          "socialblade_client_error",
          s"Unexpected status code $statusCode calling Social blade : $body"
        )
        // Act as the username does not exist in social blade
        None

      case Response(Left(error), statusCode, _, _, _, _) =>
        if (statusCode.code == 404) {
          logger.infoWithTitle("socialblade_client_notfound", s"${username} not found for ${platform.entryName}")
        } else {
          logger.errorWithTitle("socialblade_client_error", s"Error $statusCode calling Social blade : $error")
        }
        // Act as the username does not exist in social blade
        None
    }

  }

  private def handleSuccessResponse(
      body: String,
      username: String,
      platform: SocialNetworkSlug
  ): Option[CertifiedInflencerResponse] =
    Try(Json.parse(body))
      .map { jsonBody =>
        jsonBody.validate[SocialBladeResponse] match {
          case JsSuccess(response, _) =>
            val found = response.data.id.username.equalsIgnoreCase(username)
            val followers = response.data.statistics.total.subscribers
              .orElse(response.data.statistics.total.followers)
              .orElse(response.data.statistics.total.likes)

            if (found) {
              logger.infoWithTitle("socialblade_client_found", s"${username} found for ${platform.entryName}")
            } else {
              logger.infoWithTitle("socialblade_client_notfound", s"${username} not found for ${platform.entryName}")
            }

            Some(CertifiedInflencerResponse(username, followers))
          case JsError(errors) =>
            logger.errorWithTitle(
              "socialblade_client_error",
              s"Cannot parse json to SocialBladeResponse: ${jsonBody.toString}, errors : ${errors}"
            )
            // Act as the username does not exist in social blade
            None
        }
      }
      .recover { case exception: Exception =>
        logger.errorWithTitle("socialblade_client_error", "Cannot parse SocialBladeResponse to json", exception)
        // Act as the username does not exist in social blade
        None
      }
      .getOrElse(None)

}

object SocialBladeClient {
  val SocialBladeSupportedSocialNetwork: Set[SocialNetworkSlug] =
    Set(YouTube, Facebook, Instagram, TikTok, Twitter, Twitch)
}

case class CertifiedInflencerResponse(username: String, followers: Option[Int])
