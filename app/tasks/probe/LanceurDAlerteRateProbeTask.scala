package tasks.probe

import org.apache.pekko.actor.ActorSystem
import config.TaskConfiguration
import orchestrators.ProbeOrchestrator
import orchestrators.ProbeOrchestrator.ExpectedRange
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask

import java.time.LocalTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class LanceurDAlerteRateProbeTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    probeOrchestrator: ProbeOrchestrator,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(101, "lanceur_dalerte_probe", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 12.hours

  override def runTask(): Future[Unit] =
    for {
      maybePercentage <- probeRepository.getLanceurDalertePercentage(interval)
      _ <- probeOrchestrator.handleProbeResult(
        "Pourcentage de signalements 'Lanceur d'alerte'",
        maybePercentage,
        ExpectedRange(min = Some(0.1), max = Some(5)),
        interval
      )
    } yield ()

}
