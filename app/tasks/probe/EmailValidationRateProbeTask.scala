package tasks.probe

import config.TaskConfiguration
import orchestrators.ProbeOrchestrator
import orchestrators.ProbeOrchestrator.ExpectedRange
import org.apache.pekko.actor.ActorSystem
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask

import java.time.LocalTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class EmailValidationRateProbeTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    probeOrchestrator: ProbeOrchestrator,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(102, "email_validate_probe", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger = Logger(this.getClass)
  // TODO comment faire pour que ça se lance dès le démarrage de l'api (ou genre 10 minutes après ?)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 1.hour

  override def runTask(): Future[Unit] =
    for {
      maybePercentage <- probeRepository.getValidatedEmailsPercentage(interval)
      _ <- probeOrchestrator.handleProbeResult(
        "Pourcentage d'emails que les consos ont validés avec succès",
        maybePercentage,
        ExpectedRange(min = Some(50)),
        interval
      )
    } yield ()

}
