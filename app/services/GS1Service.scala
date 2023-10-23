package services

import config.GS1Configuration
import models.gs1.GS1APIProduct
import models.gs1.OAuthAccessToken
import play.api.Logger
import services.GS1Service.GS1Error
import sttp.capabilities
import sttp.client3.playJson.asJson
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.model.Header
import sttp.model.StatusCode

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait GS1ServiceInterface {
  def authenticate(): Future[OAuthAccessToken]
  def getProductByGTIN(
      accessToken: OAuthAccessToken,
      gtin: String
  ): Future[Either[GS1Service.TokenExpired.type, Option[GS1APIProduct]]]
}

class GS1Service(gs1Configuration: GS1Configuration)(implicit ec: ExecutionContext) extends GS1ServiceInterface {

  val logger: Logger                                                = Logger(this.getClass)
  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  val BaseUrl = "https://api.gs1.fr"

  override def authenticate(): Future[OAuthAccessToken] = {
    val url = uri"$BaseUrl/auth/connect/token"
    val request = basicRequest
      .body(
        Map(
          "client_id"     -> gs1Configuration.clientId,
          "client_secret" -> gs1Configuration.clientSecret,
          "scope"         -> "Products.Read",
          "grant_type"    -> "client_credentials"
        )
      )
      .post(url)
      .response(asJson[OAuthAccessToken])

    request
      .send(backend)
      .flatMap { response =>
        if (response.code.isSuccess) {
          response.body match {
            case Right(token) =>
              Future.successful(token)
            case Left(error) =>
              logger.error(s"Error while parsing oauth token from GS1", error)
              Future.failed(GS1Error("Error while parsing oauth token from GS1"))
          }
        } else {
          Future.failed(GS1Error(s"Error while fetching oauth token from GS1, code: ${response.code}"))
        }
      }
  }

  override def getProductByGTIN(
      accessToken: OAuthAccessToken,
      gtin: String
  ): Future[Either[GS1Service.TokenExpired.type, Option[GS1APIProduct]]] = {
    val url = uri"$BaseUrl/products/$gtin?api-version=v2"

    val request = basicRequest
      .headers(
        Header.authorization("Bearer", accessToken.token),
        Header("Ocp-Apim-Subscription-Key", gs1Configuration.subscriptionKey)
      )
      .get(url)
      .response(asJson[GS1APIProduct])

    request
      .send(backend)
      .flatMap { response =>
        if (response.code.isSuccess) {
          response.body match {
            case Right(product) => Future.successful(Right(Some(product)))
            case Left(_)        => Future.successful(Right(None))
          }
        } else if (response.code == StatusCode.Unauthorized || response.code == StatusCode.Forbidden) {
          Future.successful(Left(GS1Service.TokenExpired))
        } else {
          Future.successful(Right(None))
        }
      }
  }
}

object GS1Service {
  case object TokenExpired

  case class GS1Error(message: String) extends Throwable
}
