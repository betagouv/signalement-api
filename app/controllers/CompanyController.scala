package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.libs.ws._
import play.api.mvc.{ResponseHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

class CompanyController @Inject()(ws: WSClient)
                                 (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getCompanies(search: String, postalCode: Option[String], maxCount: Int) = Action.async { implicit request =>

    logger.debug(s"getCompanies [$search, $postalCode, $maxCount]")

    var request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/full_text/$search")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    if (postalCode.isDefined) {
      request = request.addQueryStringParameters("code_postal" -> postalCode.get)
    }
    
    request = request.addQueryStringParameters("per_page" -> maxCount.toString)

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }

  def getSuggestions(search: String) = Action.async { implicit request =>

    logger.debug(s"getSuggestions [$search]")

    val request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/suggest/$search")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }
}