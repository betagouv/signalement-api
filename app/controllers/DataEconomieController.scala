package controllers

import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppErrorTransformer.handleError
import orchestrators.DataEconomieOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import utils.silhouette.auth.AuthEnv

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DataEconomieController @Inject() (
    service: DataEconomieOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit val executionContext: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def reportDataEcomonie(): Action[Unit] = UnsecuredAction.async(parse.empty) { _ =>
    service
      .getReportDataEconomie()
      .map(Json.toJson(_))
      .map(Ok(_))
      .recover { case err => handleError(err) }
  }

}
