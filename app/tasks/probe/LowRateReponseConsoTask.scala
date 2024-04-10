package tasks.probe

import akka.actor.ActorSystem
import config.TaskConfiguration
import models.UserRole
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.tasklock.TaskRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsAdmin.AdminProbeTriggered
import services.emails.MailService
import tasks.ScheduledTask
import utils.Logs.RichLogger

import java.time.LocalTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class LowRateReponseConsoTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository,
    userRepository: UserRepositoryInterface,
    mailService: MailService
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(100, "low_rate_reponse_conso", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 12.hours

  override def runTask(): Future[Unit] = probeRepository.getReponseConsoRate(interval).flatMap {
    case Some(rate) if rate < 1.0d =>
      logger.warnWithTitle("probe_triggered", s"Taux de signalements 'Réponse conso' faible : $rate%")
      for {
        users <- userRepository.listForRoles(Seq(UserRole.Admin))
        _ <- mailService
          .send(
            AdminProbeTriggered.build(users.map(_.email), "Taux de signalements 'Réponse conso' faible", rate, "bas")
          )
      } yield ()
    case rate =>
      logger.debug(s"Taux de signalements correct: $rate%")
      Future.unit
  }
}
