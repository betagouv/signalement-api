package services

import play.api.Logger
import play.api.libs.json.JsValue
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson.asJson

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait OpenBeautyFactsServiceInterface {
  def getProductByBarcode(barcode: String): Future[Option[JsValue]]
}

class OpenBeautyFactsService(implicit ec: ExecutionContext) extends OpenBeautyFactsServiceInterface {
  val logger: Logger                                                = Logger(this.getClass)
  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  val BaseUrl = "https://world.openbeautyfacts.org/api/v3/product"

  override def getProductByBarcode(barcode: String): Future[Option[JsValue]] = {
    val url = uri"$BaseUrl/$barcode"
    val request = basicRequest
      .get(url)
      .response(asJson[JsValue])

    request
      .send(backend)
      .flatMap { response =>
        if (response.code.isSuccess) {
          response.body match {
            case Right(product) =>
              logger.debug(s"Product found")
              Future.successful(Some(product))
            case Left(error) =>
              logger.debug(s"Product not found", error)
              Future.successful(None)
          }
        } else {
          logger.warn(s"Error while calling Open Beauty Facts: ${response.code}")
          Future.successful(None)
        }
      }
  }
}
