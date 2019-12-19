package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import play.api.Logger
import play.api.http.Status._
import play.api.libs.ws._
import utils.silhouette.auth.AuthEnv

import scala.concurrent.{ExecutionContext, Future}

case class Location(lat: Double, lon: Double)

class CompanyController @Inject()(ws: WSClient, val silhouette: Silhouette[AuthEnv])
                                 (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getCompanies(search: String, postalCode: Option[String], maxCount: Int) = UnsecuredAction.async { implicit request =>

    logger.debug(s"getCompanies [$search, $postalCode, $maxCount]")

    var request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/full_text/$search")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    if (postalCode.isDefined) {
      request = request.addQueryStringParameters("code_postal" -> postalCode.get)
    }
    
    request = request.addQueryStringParameters("per_page" -> maxCount.toString)

    request.get().flatMap(
      response => {
        response.status match {
          case NOT_FOUND => Future(NotFound(response.json))
          case status if isServerError(status) =>
            logger.error(s"getCompanies [$search, $postalCode, $maxCount] - $response")
            Future(InternalServerError(response.json))
          case _ => Future(Ok(response.json))
        }
      }
    );

  }

  def getCompaniesBySiret(siret: String, maxCount: Int) = UnsecuredAction.async { implicit request =>

    logger.debug(s"getCompaniesBySiret [$siret]")

    var request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/siret/$siret")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request = request.addQueryStringParameters("per_page" -> maxCount.toString)

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case status if isServerError(status) =>
          logger.error(s"getCompaniesBySiret [$siret] - $response")
          Future(InternalServerError(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }
}