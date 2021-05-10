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
class EnterpriseController @Inject()(
  enterpriseSyncInfoRepository: EnterpriseSyncInfoRepository,
  enterpriseSyncOrchestrator: EnterpriseSyncOrchestrator,
  val silhouette: Silhouette[AuthEnv],
)(implicit ec: ExecutionContext) extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds


  def syncAll = UnsecuredAction { implicit request =>
    enterpriseSyncOrchestrator.syncStockEntreprise.map(_ => {
      enterpriseSyncOrchestrator.syncStockUniteLegale
    })
    Ok
  }

  def stopAll = UnsecuredAction { implicit request =>
    enterpriseSyncOrchestrator.stopStockEntreprise
    Ok
  }

  def syncEtablissement = SecuredAction { implicit request =>
    Ok
  }

  def syncUniteLegale = SecuredAction { implicit request =>
    Ok
  }

  def getSyncInfo = SecuredAction { implicit request =>
    Ok
  }
}
