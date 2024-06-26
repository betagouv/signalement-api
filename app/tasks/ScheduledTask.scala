package tasks

import config.TaskConfiguration
import org.apache.pekko.actor.ActorSystem
import play.api.Logger
import repositories.tasklock.TaskDetails
import repositories.tasklock.TaskRepositoryInterface
import tasks.model.TaskSettings
import tasks.model.TaskSettings._
import tasks.model.TaskSettings.frequentTasksInitialDelay
import utils.Logs.RichLogger

import java.time.LocalTime
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
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
  val taskSettings: TaskSettings

  private def createOrUpdateTaskDetails(taskDetails: TaskDetails) =
    taskRepository
      .createOrUpdate(taskDetails)
      .map(_ => logger.debug(s"$taskName updated in DB"))
      .recover(err => logger.errorWithTitle("task_failed", s"$taskName failed", err))

  def runTask(): Future[Unit]

  private def runTaskWithLock(interval: FiniteDuration, maybeDailyStartTime: Option[LocalTime]): Unit =
    (for {
      lockAcquired <- taskRepository.acquireLock(taskId)
      _ <-
        if (lockAcquired) {
          logger.info(s"Lock acquired for $taskName.")
          val actualStartTime = OffsetDateTime.now()
          val taskDetails =
            TaskDetails(
              taskId,
              taskName,
              startTime = maybeDailyStartTime,
              interval,
              lastRunDate = actualStartTime,
              lastRunError = None
            )
          runTask()
            .flatMap { _ =>
              logger.info(s"$taskName finished")
              createOrUpdateTaskDetails(taskDetails)
            }
            .recoverWith { err =>
              logger.errorWithTitle("task_failed", s"$taskName failed", err)
              createOrUpdateTaskDetails(taskDetails.copy(lastRunError = Some(err.getMessage)))
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
    val (initialDelay, interval, maybeDailyStartTime) = taskSettings match {
      case DailyTaskSettings(startTime)   => (computeInitialDelay(startTime), 24.hours, Some(startTime))
      case FrequentTaskSettings(interval) => (frequentTasksInitialDelay, interval, None)
    }
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay,
      interval
    ) { () =>
      if (taskConfiguration.active) {
        logger.infoWithTitle("task_launch", s"$taskName launched")
        runTaskWithLock(interval, maybeDailyStartTime)
      } else logger.info(s"$taskName not launched, tasks are disabled")
    }: Unit
    val nextRunApproximateTime = computeDateTimeCorrespondingToDelay(initialDelay)
    logger.info(s"$taskName scheduled for $nextRunApproximateTime (in $initialDelay) and then every $interval")
  }
}
