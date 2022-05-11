package controllers

import com.mohiva.play.silhouette.api.Silhouette
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import utils.Country
import utils.silhouette.auth.AuthEnv

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ConstantController @Inject() (val silhouette: Silhouette[AuthEnv], controllerComponents: ControllerComponents)(
    implicit val ec: ExecutionContext
) extends BaseController(controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def getCountries = UnsecuredAction.async {
    Future(Ok(Json.toJson(Country.countries)))
  }

}
