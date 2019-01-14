package controllers

import javax.inject._

import scala.concurrent.ExecutionContext


@Singleton
class StaticController @Inject()(implicit ec: ExecutionContext) extends BaseController {

  def api = Action { implicit request =>
    Ok(views.html.api())
  }
}
