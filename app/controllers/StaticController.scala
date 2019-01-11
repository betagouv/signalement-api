package controllers

import javax.inject._
import play.api.Logger

import scala.concurrent.ExecutionContext


@Singleton
class StaticController @Inject()(implicit ec: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def index = Action { implicit request =>
    logger.debug("index")
    Ok(views.html.index())
  }
}
