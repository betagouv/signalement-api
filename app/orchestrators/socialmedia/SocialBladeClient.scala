package orchestrators.socialmedia

import config.SocialBladeClientConfiguration
import models.report.SocialNetworkSlug
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
  ): Future[Boolean] = {

    val request: RequestT[Identity, Either[String, String], Any] = basicRequest
      .get(uri"${config.url}/b/${platform.entryName.toLowerCase}/statistics")
      .header("query", username.toLowerCase)
      .header("clientid", config.clientId)
      .header("token", config.token)
      .header("history", "default")
      .response(asString)

    request.send(backend).map {
      case Response(Right(body), statusCode, _, _, _, _) if statusCode.isSuccess =>
        handleSuccessResponse(body, username)

      case Response(Right(body), statusCode, _, _, _, _) =>
        logger.errorWithTitle(
          "socialblade_client_error",
          s"Unexpected status code $statusCode calling Social blade : $body"
        )
        // Act as the username does not exist in social blade
        false

      case Response(Left(error), statusCode, _, _, _, _) =>
        logger.errorWithTitle("socialblade_client_error", s"Error $statusCode calling Social blade : $error")
        // Act as the username does not exist in social blade
        false
    }

  }

  private def handleSuccessResponse(body: String, username: String): Boolean =
    Try(Json.parse(body))
      .map { jsonBody =>
        jsonBody.validate[SocialBladeResponse] match {
          case JsSuccess(response, _) =>
            response.data.id.username.equalsIgnoreCase(username)
          case JsError(errors) =>
            logger.errorWithTitle(
              "socialblade_client_error",
              s"Cannot parse json to SocialBladeResponse: ${jsonBody.toString}, errors : ${errors}"
            )
            // Act as the username does not exist in social blade
            false
        }
      }
      .recover { case exception: Exception =>
        logger.errorWithTitle("socialblade_client_error", "Cannot parse SocialBladeResponse to json", exception)
        // Act as the username does not exist in social blade
        false
      }
      .getOrElse(false)

}
