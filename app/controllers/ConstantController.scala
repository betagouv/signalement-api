package controllers

import models.User
import models.report.ReportCategory
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import utils.Country
import utils.auth.Authenticator

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
    Future(Ok(Json.toJson(ReportCategory.values.filterNot(_.legacy))))
  }

}
