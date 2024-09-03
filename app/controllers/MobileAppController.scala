package controllers

import authentication.Authenticator
import config.SignalConsoConfiguration
import models.User
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

class MobileAppController(
    val signalConsoConfiguration: SignalConsoConfiguration,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def getRequirements = IpRateLimitedAction2 {
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
    Ok(json)
  }

}
