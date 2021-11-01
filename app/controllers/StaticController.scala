package controllers

import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv

import javax.inject._

@Singleton
class StaticController @Inject() (val silhouette: Silhouette[AuthEnv]) extends BaseController {

  def api = UserAwareAction { _ =>
    Ok(views.html.api())
  }
}
