package orchestrators.proconnect

import config.ProConnectConfiguration
import orchestrators.proconnect.ProConnectClient.ProConnectError
import play.api.Logger
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.UriContext
import sttp.client3.asString
import sttp.client3.basicRequest
import sttp.client3.playJson.asJson
import sttp.model.Header

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ProConnectClient(config: ProConnectConfiguration)(implicit ec: ExecutionContext) {

  val logger  = Logger(this.getClass)
  val backend = AsyncHttpClientFutureBackend()

  def getToken(code: String): Future[ProConnectAccessToken] = {
    val url = s"${config.url}/${config.tokenEndpoint}"
    val request = basicRequest
      .body(
        Map(
          "client_id"     -> config.clientId,
          "client_secret" -> config.clientSecret,
          "grant_type"    -> "authorization_code",
          "redirect_uri"  -> config.loginRedirectUri.toString,
          "code"          -> code
        )
      )
      .post(uri"${url}")
      .response(asJson[ProConnectAccessToken])

    request
      .send(backend)
      .flatMap { response =>
        if (response.code.isSuccess) {
          response.body match {
            case Right(token) =>
              Future.successful(token)
            case Left(error) =>
              logger.error(s"Error while parsing oauth token from ProConnect", error)
              Future.failed(ProConnectError("Error while parsing oauth token from ProConnect"))
          }
        } else {
          logger.error(response.toString())
          Future.failed(ProConnectError(s"Error while fetching oauth token from ProConnect, code: ${response.code}"))
        }
      }
  }

  def userInfo(token: ProConnectAccessToken): Future[String] = {
    val url = uri"${config.url}/api/v2/userinfo"
    val request = basicRequest
      .get(url)
      .headers(Header.authorization("Bearer", token.access_token))
      .response(asString)
    request
      .send(backend)
      .flatMap { response =>
        if (response.code.isSuccess) {
          response.body match {
            case Right(token) =>
              Future.successful(token)
            case Left(error) =>
              logger.error(s"Error while parsing oauth token from ProConnect : $error")
              Future.failed(ProConnectError("Error while parsing oauth token from ProConnect"))
          }
        } else {
          Future.failed(ProConnectError(s"Error while fetching oauth token from ProConnect, code: ${response.code}"))
        }
      }
  }

  def endSessionUrl(id_token: String, state: String) =
//    val logoutRedirectUri= URLEncoder.encode(config.logoutRedirectUri.toString, StandardCharsets.UTF_8.toString)
    uri"${config.url}/api/v2/session/end?id_token_hint=${id_token}&state=${state}&post_logout_redirect_uri=${config.logoutRedirectUri.toString}"
      .toString()

}

object ProConnectClient {
  case object TokenExpired

  case class ProConnectError(message: String) extends Throwable
}
