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

class ReponseConsoRateProbeTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    probeOrchestrator: ProbeOrchestrator,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(100, "low_rate_reponse_conso", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 12.hours

  override def runTask(): Future[Unit] =
    for {
      maybeRate <- probeRepository.getReponseConsoRate(interval)
      _ <- probeOrchestrator.handleProbeResult(
        maybeRate,
        _ < 1.0d,
        "Taux de signalements 'Réponse conso'",
        "bas"
      )
    } yield ()

}
