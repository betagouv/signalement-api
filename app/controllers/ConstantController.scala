package controllers

import authentication.Authenticator
import models.User
import models.report.ReportCategory
import models.report.ReportCategoryStatus
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import utils.Country

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ConstantController(authenticator: Authenticator[User], controllerComponents: ControllerComponents)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def getCountries = Action.async {
    Future(Ok(Json.toJson(Country.countries)))
  }

  def getCategories = Action.async {
    Future(Ok(Json.toJson(ReportCategory.values.filter(_.status != ReportCategoryStatus.Legacy))))
  }

  def getCategoriesByStatus() = Action.async {
    val legacy   = ReportCategory.values.filter(_.status == ReportCategoryStatus.Legacy)
    val closed   = ReportCategory.values.filter(_.status == ReportCategoryStatus.Closed)
    val inactive = ReportCategory.values.filter(_.status == ReportCategoryStatus.Inactive)
    val active   = ReportCategory.values.filter(_.status == ReportCategoryStatus.Active)
    val json = Json.obj(
      "active"   -> active,
      "inactive" -> inactive,
      "legacy"   -> legacy,
      "closed"   -> closed
    )
    Future(Ok(json))
  }

}
