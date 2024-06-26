package tasks.model

import java.time.LocalTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

trait TaskSettings

object TaskSettings {

  // Task that run every night at a given time
  case class DailyTaskSettings(
      startTime: LocalTime
  ) extends TaskSettings

  // Task that start 2 minutes after every startup, and then every X hours for example
  // For tasks that don't need to be run at a specific time
  // but need to be run frequently
  case class FrequentTaskSettings(
      interval: FiniteDuration
  ) extends TaskSettings
  val frequentTasksInitialDelay = 2.minutes

}
