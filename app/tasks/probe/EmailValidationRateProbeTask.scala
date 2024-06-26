package tasks.probe

import config.TaskConfiguration
import orchestrators.ProbeOrchestrator
import orchestrators.ProbeOrchestrator.ExpectedRange
import org.apache.pekko.actor.ActorSystem
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class EmailValidationRateProbeTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    probeOrchestrator: ProbeOrchestrator,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(102, "email_validate_probe", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger = Logger(this.getClass)
  override val taskSettings   = FrequentTaskSettings(interval = 1.hour)

  override def runTask(): Future[Unit] = {
    val evaluationPeriod = 1.hour
    for {
      maybePercentage <- probeRepository.getValidatedEmailsPercentage(evaluationPeriod)
      _ <- probeOrchestrator.handleProbeResult(
        "Pourcentage d'emails que les consos ont validés avec succès",
        maybePercentage,
        ExpectedRange(min = Some(50)),
        evaluationPeriod
      )
    } yield ()
  }

}
