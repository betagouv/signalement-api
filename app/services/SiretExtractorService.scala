package services

import config.SiretExtractorConfiguration
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.Response
import sttp.client3.ResponseException
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson._
import sttp.model.Header
import tasks.website.ExtractionResultApi

import scala.concurrent.Future

class SiretExtractorService(siretExtractorConfiguration: SiretExtractorConfiguration) {

  val logger: Logger = Logger(this.getClass)

  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  def extractSiret(
      website: String
  ): Future[Response[Either[ResponseException[String, JsError], ExtractionResultApi]]] = {
    logger.debug(s"Calling siret extractor with website $website")
    val url = uri"${siretExtractorConfiguration.url}/extract"
    val request = basicRequest
      .headers(Header("X-Api-Key", siretExtractorConfiguration.apiKey))
      .post(url)
      .response(asJson[JsValue])
      .body(Json.obj("website" -> website))
      .response(asJson[ExtractionResultApi])

    request.send(backend)
  }
}
