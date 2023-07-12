package controllers

import com.mohiva.play.silhouette.api.Silhouette
import config.SignalConsoConfiguration
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MobileAppController(
    val signalConsoConfiguration: SignalConsoConfiguration,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def getRequirements = UnsecuredAction.async {
    val json = JsObject(
      Seq(
        "minAppVersion" -> JsObject(
          Seq(
            "ios" -> JsString(signalConsoConfiguration.mobileApp.minimumAppVersionIos),
            "android" -> JsString(signalConsoConfiguration.mobileApp.minimumAppVersionAndroid)
          )
        )
      )
    )
    Future(Ok(json))
  }

}
