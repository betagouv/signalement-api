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

class ReponseConsoRateProbeTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    probeOrchestrator: ProbeOrchestrator,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(100, "reponseconso_probe", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 12.hours

  override def runTask(): Future[Unit] =
    for {
      maybePercentage <- probeRepository.getReponseConsoPercentage(interval)
      _ <- probeOrchestrator.handleProbeResult(
        "Pourcentage de signalements 'Réponse conso'",
        maybePercentage,
        ExpectedRange(min = Some(1), max = Some(40)),
        interval
      )
    } yield ()

}
