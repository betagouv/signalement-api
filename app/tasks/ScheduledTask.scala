package tasks

import akka.actor.ActorSystem
import config.TaskConfiguration
import play.api.Logger
import repositories.tasklock.TaskRepositoryInterface
import repositories.tasklock.TaskDetails
import utils.Logs.RichLogger

import java.time.LocalTime
import java.time.OffsetDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

abstract class ScheduledTask(
    taskId: Int,
    taskName: String,
    taskRepository: TaskRepositoryInterface,
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration
)(implicit ec: ExecutionContext) {

  val logger: Logger
  val startTime: LocalTime
  val interval: FiniteDuration

  private def createTaskDetails(error: Option[String]) =
    TaskDetails(taskId, taskName, startTime, interval, lastRunDate = OffsetDateTime.now(), error)

  private def createOrUpdateTaskDetails(taskDetails: TaskDetails) =
    taskRepository
      .createOrUpdate(taskDetails)
      .map(_ => logger.debug(s"$taskName updated in DB"))
      .recover(err => logger.errorWithTitle("task_failed", s"$taskName failed", err))

  def runTask(): Future[Unit]

  private def runTaskWithLock(): Unit =
    (for {
      lockAcquired <- taskRepository.acquireLock(taskId)
      _ <-
        if (lockAcquired) {
          logger.info(s"Lock acquired for $taskName.")
          runTask()
            .flatMap { _ =>
              logger.info(s"$taskName finished")
              createOrUpdateTaskDetails(createTaskDetails(None))
            }
            .recoverWith { err =>
              logger.errorWithTitle("task_failed", s"$taskName failed", err)
              createOrUpdateTaskDetails(createTaskDetails(Some(err.getMessage)))
            }
        } else {
          logger.info(s"Lock for $taskName is already taken by another instance. Nothing to do here.")
          Future.unit
        }
    } yield ()).onComplete(_ => release())

  private def release(): Unit =
    actorSystem.scheduler.scheduleOnce(1.minute) {
      logger.debug(s"Releasing lock for $taskName with id $taskId")
      taskRepository.releaseLock(taskId).onComplete {
        case Success(_) =>
          logger.debug(s"Lock released for $taskName with id $taskId")
        case Failure(err) =>
          logger.error(s"Fail to release lock for $taskName with id $taskId", err)
      }
    }: Unit

  def schedule(): Unit = {
    val initialDelay = computeStartingTime(startTime)
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay,
      interval
    ) { () =>
      if (taskConfiguration.active) {
        logger.infoWithTitle("task_launch", s"$taskName launched")
        runTaskWithLock()
      } else logger.info(s"$taskName not launched, tasks are disabled")
    }: Unit
    logger.info(s"$taskName scheduled for $startTime (in $initialDelay)")
  }
}
