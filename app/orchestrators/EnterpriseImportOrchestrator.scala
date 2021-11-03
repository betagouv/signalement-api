package orchestrators

import actors.EnterpriseSyncActor
import akka.actor.ActorRef
import akka.pattern.ask
import models.EnterpriseImportInfo
import models.EtablissementFile
import models.UniteLegaleFile
import repositories.EnterpriseImportInfoRepository

import javax.inject.Inject
import javax.inject.Named
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class EnterpriseImportOrchestrator @Inject() (
    enterpriseSyncInfoRepository: EnterpriseImportInfoRepository,
    @Named("enterprise-sync-actor") enterpriseActor: ActorRef
)(implicit val executionContext: ExecutionContext) {

  implicit val timeout: akka.util.Timeout = 5.seconds

  private[this] lazy val startEtablissementFileActor = EnterpriseSyncActor.Start(EtablissementFile)

  private[this] lazy val startUniteLegaleFileActor = EnterpriseSyncActor.Start(UniteLegaleFile)

  def getLastEtablissementImportInfo(): Future[Option[EnterpriseImportInfo]] =
    enterpriseSyncInfoRepository.findLast(EtablissementFile.name)

  def getUniteLegaleImportInfo(): Future[Option[EnterpriseImportInfo]] =
    enterpriseSyncInfoRepository.findLast(UniteLegaleFile.name)

  def startEtablissementFile =
    enterpriseActor ? startEtablissementFileActor

  def startUniteLegaleFile =
    enterpriseActor ? startUniteLegaleFileActor

  def cancelEntrepriseFile =
    enterpriseActor ? EnterpriseSyncActor.Cancel(EtablissementFile.name)

  def cancelUniteLegaleFile =
    enterpriseActor ? EnterpriseSyncActor.Cancel(UniteLegaleFile.name)

}
