package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.libs.ws._

import scala.concurrent.{ExecutionContext, Future}

class EntrepriseController @Inject()(ws: WSClient)
                                     (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def test = Action.async { implicit request =>

    val request = ws.url("https://entreprise.data.gouv.fr/api/sirene/v1/full_text/jerome")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request.get().flatMap( result => {
      logger.debug(s"result ${result.json}")
      Future(Ok(result.json))
    }
    );


  }
}