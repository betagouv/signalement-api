package services

import config.WebsiteApiConfiguration
import models.report.ArborescenceNode
import play.api.Logger
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson.asJson

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class MinimizedAnomalies(fr: List[ArborescenceNode], en: List[ArborescenceNode])

object MinimizedAnomalies {
  implicit val reads: Reads[MinimizedAnomalies] = (json: JsValue) =>
    for {
      fr <- (json \ "fr").validate[JsArray]
      en <- (json \ "en").validate[JsArray]
    } yield MinimizedAnomalies(fr = ArborescenceNode.fromJson(fr), en = ArborescenceNode.fromJson(en))
}

trait WebsiteApiServiceInterface {
  def fetchMinimizedAnomalies(): Future[Option[MinimizedAnomalies]]
}

class WebsiteApiService(websiteApiConfiguration: WebsiteApiConfiguration)(implicit ec: ExecutionContext)
    extends WebsiteApiServiceInterface {
  val logger: Logger                                                = Logger(this.getClass)
  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  def fetchMinimizedAnomalies(): Future[Option[MinimizedAnomalies]] = {
    val url = uri"${websiteApiConfiguration.url}/v1/categories/minimized"
    val request = basicRequest
      .get(url)
      .response(asJson[MinimizedAnomalies])

    request
      .send(backend)
      .flatMap { response =>
        if (response.code.isSuccess) {
          response.body match {
            case Right(anomalies) =>
              logger.debug(s"Minimized anomalies correctly fetched")
              Future.successful(Some(anomalies))
            case Left(error) =>
              logger.debug(s"Error while fetching anomalies", error)
              Future.successful(None)
          }
        } else {
          logger.warn(s"Error while calling Website API: ${response.code}")
          Future.successful(None)
        }
      }
  }
}
