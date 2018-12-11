package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.libs.ws._
import play.api.mvc.{ResponseHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

class EntrepriseController @Inject()(ws: WSClient)
                                     (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getCompanies(name: String, city: String, maxCount: Int) = Action.async { implicit request =>

    val request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/full_text/$name $city?per_page=$maxCount")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }
}