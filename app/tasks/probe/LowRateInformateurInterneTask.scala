package tasks.probe

import org.apache.pekko.actor.ActorSystem
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

class LowRateInformateurInterneTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository,
    userRepository: UserRepositoryInterface,
    mailService: MailService
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(101, "low_rate_informateur_entreprise", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 12.hours

  override def runTask(): Future[Unit] = probeRepository.getInformateurInterneRate(interval).flatMap {
    case Some(rate) if rate < 0.1d =>
      logger.warnWithTitle("probe_triggered", s"Taux de signalements 'Informateur interne' faible : $rate%")
      for {
        users <- userRepository.listForRoles(Seq(UserRole.Admin))
        _ <- mailService
          .send(
            AdminProbeTriggered
              .Email(users.map(_.email), "Taux de signalements 'Informateur interne' faible", rate, "bas")
          )
      } yield ()
    case rate =>
      logger.debug(s"Taux de signalements correct: $rate%")
      Future.unit
  }
}
