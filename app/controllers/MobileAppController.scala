package controllers

import config.SignalConsoConfiguration
import models.User
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.mvc.ControllerComponents
import utils.auth.Authenticator

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MobileAppController(
    val signalConsoConfiguration: SignalConsoConfiguration,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def getRequirements = Action.async {
    val json = JsObject(
      Seq(
        "minAppVersion" -> JsObject(
          Seq(
            "ios"     -> JsString(signalConsoConfiguration.mobileApp.minimumAppVersionIos),
            "android" -> JsString(signalConsoConfiguration.mobileApp.minimumAppVersionAndroid)
          )
        )
      )
    )
    Future(Ok(json))
  }

}
