package tasks.probe

import org.apache.pekko.actor.ActorSystem
import config.TaskConfiguration
import orchestrators.ProbeOrchestrator
import orchestrators.ProbeOrchestrator.ExpectedRange
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class LanceurDAlerteRateProbeTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    probeOrchestrator: ProbeOrchestrator,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(101, "lanceur_dalerte_probe", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger = Logger(this.getClass)
  override val taskSettings   = FrequentTaskSettings(interval = 6.hour)

  override def runTask(): Future[Unit] = {
    val evaluationPeriod = 12.hours
    for {
      maybePercentage <- probeRepository.getLanceurDalertePercentage(evaluationPeriod)
      _ <- probeOrchestrator.handleProbeResult(
        "Pourcentage de signalements 'Lanceur d'alerte'",
        maybePercentage,
        ExpectedRange(min = Some(0.1), max = Some(5)),
        evaluationPeriod
      )
    } yield ()
  }

}
