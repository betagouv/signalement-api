package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.UserRoles
import play.api.libs.json.JsError
import play.api.libs.json.Json
import repositories.ReportNotificationBlocklistRepository
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ReportNotificationBlocklistController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    val silhouetteAPIKey: Silhouette[APIKeyEnv],
    val repository: ReportNotificationBlocklistRepository
)(implicit
    ec: ExecutionContext
) extends BaseController {

  def getAll() = SecuredAction(WithRole(UserRoles.Pro)).async { implicit request =>
    repository.findByUser(request.identity.id).map(entities => Ok(Json.toJson(entities)))
  }

  def create(companyId: UUID) = SecuredAction(WithRole(UserRoles.Pro)).async { implicit request =>
    repository.create(request.identity.id, companyId).map(entity => Ok(Json.toJson(entity)))
  }

  def delete(companyId: UUID) = SecuredAction(WithRole(UserRoles.Pro)).async { implicit request =>
    repository.delete(request.identity.id, companyId).map(_ => Ok)
  }
}
