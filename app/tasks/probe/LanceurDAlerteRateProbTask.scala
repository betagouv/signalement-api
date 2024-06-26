package tasks.probe

import org.apache.pekko.actor.ActorSystem
import config.TaskConfiguration
import orchestrators.ProbeOrchestrator
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask

import java.time.LocalTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class LanceurDAlerteRateProbTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    probeOrchestrator: ProbeOrchestrator,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(101, "low_rate_lanceur_dalerte", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 12.hours

  override def runTask(): Future[Unit] =
    for {
      maybeRate <- probeRepository.getLancerDalerteRate(interval)
      _ <- probeOrchestrator.handleProbeResult(
        maybeRate,
        _ < 0.1d,
        "Taux de signalements 'Lanceur d'alerte'",
        "bas"
      )
    } yield ()

}
