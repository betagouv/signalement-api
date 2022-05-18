package controllers

import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class StaticController @Inject() (val silhouette: Silhouette[AuthEnv])(implicit val ec: ExecutionContext)
    extends BaseController {

  def api = UserAwareAction.async(parse.empty) { _ =>
    Future.successful(Ok(views.html.api()))
  }
}
