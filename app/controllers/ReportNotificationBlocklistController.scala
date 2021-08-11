package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.UserRoles
import orchestrators.CompaniesVisibilityOrchestrator
import repositories.ReportNotificationBlocklistRepository
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithRole}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ReportNotificationBlocklistController @Inject() (
  val silhouette: Silhouette[AuthEnv],
  val silhouetteAPIKey: Silhouette[APIKeyEnv],
  val repository: ReportNotificationBlocklistRepository,
  val visibleCompaniesOrch: CompaniesVisibilityOrchestrator,
)(implicit ec: ExecutionContext) extends BaseController {

  def findByUserId(userId: UUID) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF, UserRoles.Pro)).async { implicit request =>
    for {
      companies <- visibleCompaniesOrch.fetchViewableCompanies(request.identity)
      blockedNotif <- repository.findByUser(userId)
    } yield {
      Ok
    }
  }
}