package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import play.api.Logger
import play.api.libs.json.Json
import utils.Constants.ReportStatus.reportStatusList
import utils.Country
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ConstantController @Inject() (val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext)
    extends BaseController {
  val logger: Logger = Logger(this.getClass)

  def getReportStatus = SecuredAction.async { implicit request =>
    Future.successful(
      Ok(
        Json.toJson(
          reportStatusList.flatMap(_.getValueWithUserRole(request.identity.userRole)).distinct
        )
      )
    )
  }

  def getCountries = UnsecuredAction.async {
    Future(Ok(Json.toJson(Country.countries)))
  }

}
