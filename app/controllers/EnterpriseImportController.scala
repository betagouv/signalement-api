package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.UserRoles
import orchestrators.EnterpriseImportOrchestrator
import play.api.libs.json.Json
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class EnterpriseImportController @Inject() (
    enterpriseSyncOrchestrator: EnterpriseImportOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext)
    extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds

  def startEtablissementFile = SecuredAction(WithRole(UserRoles.Admin)) { _ =>
    enterpriseSyncOrchestrator.startEtablissementFile
    Ok
  }

  def startUniteLegaleFile = SecuredAction(WithRole(UserRoles.Admin)) { _ =>
    enterpriseSyncOrchestrator.startUniteLegaleFile
    Ok
  }

  def cancelAllFiles = SecuredAction(WithRole(UserRoles.Admin)) { _ =>
    enterpriseSyncOrchestrator.cancelUniteLegaleFile
    enterpriseSyncOrchestrator.cancelEntrepriseFile
    Ok
  }

  def cancelEtablissementFile = SecuredAction(WithRole(UserRoles.Admin)) { _ =>
    enterpriseSyncOrchestrator.cancelEntrepriseFile
    Ok
  }

  def cancelUniteLegaleFile = SecuredAction(WithRole(UserRoles.Admin)) { _ =>
    enterpriseSyncOrchestrator.cancelUniteLegaleFile
    Ok
  }

  def getSyncInfo = SecuredAction(WithRole(UserRoles.Admin)).async { _ =>
    for {
      etablissementImportInfo <- enterpriseSyncOrchestrator.getLastEtablissementImportInfo()
      uniteLegaleInfo <- enterpriseSyncOrchestrator.getUniteLegaleImportInfo()
    } yield Ok(
      Json.obj(
        "etablissementImportInfo" -> etablissementImportInfo,
        "uniteLegaleInfo" -> uniteLegaleInfo
      )
    )
  }
}
