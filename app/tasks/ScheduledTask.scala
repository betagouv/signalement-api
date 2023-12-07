package tasks

import akka.actor.ActorSystem
import config.TaskConfiguration
import play.api.Logger
import repositories.tasklock.TaskLockRepositoryInterface
import utils.Logs.RichLogger

import java.time.LocalTime
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

abstract class ScheduledTask(
    taskId: Int,
    taskName: String,
    taskLockRepository: TaskLockRepositoryInterface,
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration
)(implicit ec: ExecutionContext) {

  val logger: Logger
  val startTime: LocalTime
  val interval: FiniteDuration

  def runTask(): Future[Unit]

  private def runTaskWithLock(): Future[Unit] =
    (for {
      lockAcquired <- taskLockRepository.acquire(taskId)
      _ <-
        if (lockAcquired) runTask()
        else {
          logger.info(s"Lock for $taskName is already taken by another instance. Nothing to do here.")
          Future.unit
        }
      _ <- taskLockRepository.release(taskId)
    } yield ()).recoverWith(_ => taskLockRepository.release(taskId).map(_ => ()))

  def schedule(): Unit = {
    val initialDelay = computeStartingTime(startTime)
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay,
      interval
    ) { () =>
      if (taskConfiguration.active) {
        logger.infoWithTitle("task_launch", s"$taskName launched")
        runTaskWithLock().onComplete {
          case Success(_) =>
            logger.info(s"$taskName finished")
          case Failure(err) =>
            logger.errorWithTitle("task_failed", s"$taskName failed", err)
        }
      } else logger.info(s"$taskName not launched, tasks are disabled")
    }: Unit
    logger.info(s"$taskName scheduled for $startTime (in $initialDelay)")
  }
}
