package controllers

import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv
import javax.inject._
import scala.concurrent.ExecutionContext

@Singleton
class StaticController @Inject() (val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext)
    extends BaseController {

  def api = UserAwareAction { implicit request =>
    Ok(views.html.api())
  }
}
