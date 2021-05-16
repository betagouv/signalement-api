package controllers

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named, Singleton}
import models.EnterpriseSyncInfo
import orchestrators.EnterpriseSyncOrchestrator
import repositories.EnterpriseSyncInfoRepository
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class EnterpriseImportController @Inject()(
  enterpriseSyncInfoRepository: EnterpriseSyncInfoRepository,
  enterpriseSyncOrchestrator: EnterpriseSyncOrchestrator,
  val silhouette: Silhouette[AuthEnv],
)(implicit ec: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds


  def startEtablissementFile = UnsecuredAction { implicit request =>
    enterpriseSyncOrchestrator.startEntrepriseFile
    Ok
  }

  def startUniteLegaleFile = SecuredAction { implicit request =>
    enterpriseSyncOrchestrator.startUniteLegaleFile
    Ok
  }

  def cancelAllFiles = UnsecuredAction { implicit request =>
    enterpriseSyncOrchestrator.cancelUniteLegaleFile
    enterpriseSyncOrchestrator.cancelEntrepriseFile
    Ok
  }

  def cancelEtablissementFile = SecuredAction { implicit request =>
    enterpriseSyncOrchestrator.cancelEntrepriseFile
    Ok
  }

  def cancelUniteLegaleFile = SecuredAction { implicit request =>
    enterpriseSyncOrchestrator.cancelUniteLegaleFile
    Ok
  }

  def getSyncInfo = SecuredAction { implicit request =>
    Ok
  }
}
