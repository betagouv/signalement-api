package services

import config.SiretExtractorConfiguration
import play.api.Logger
import play.api.libs.json.JsValue
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson._
import sttp.model.Header

import scala.concurrent.Future

class SiretExtractorService(siretExtractorConfiguration: SiretExtractorConfiguration) {

  val logger: Logger = Logger(this.getClass)

  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  def extractSiret(body: Option[JsValue]) = {
    logger.debug(s"Calling siret extractor with body ${body}")
    val url = uri"${siretExtractorConfiguration.url}/extract"
    val request = basicRequest
      .headers(Header("X-Api-Key", siretExtractorConfiguration.apiKey))
      .post(url)
      .response(asJson[JsValue])

    val requestWithBody = body.fold(request)(s => request.body(s))

    requestWithBody.send(backend)
  }
}
