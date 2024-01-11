package repositories.tasklock

import repositories.PostgresProfile.api._
import repositories.TypedDatabaseTable
import slick.lifted.ProvenShape

import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import scala.jdk.DurationConverters._

class TaskDetailsTable(tag: Tag) extends TypedDatabaseTable[TaskDetails, Int](tag, "task_details") {

  def name          = column[String]("name")
  def startTime     = column[LocalTime]("start_time")
  def interval      = column[Duration]("interval")
  def lastRunDate   = column[OffsetDateTime]("last_run_date")
  def lastRunStatus = column[String]("last_run_status")

  type TaskData = (Int, String, LocalTime, Duration, OffsetDateTime, String)

  def constructTaskDetails: TaskData => TaskDetails = {
    case (id, name, startTime, interval, lastRunDate, lastRunStatus) =>
      TaskDetails(id, name, startTime, interval.toScala, lastRunDate, lastRunStatus)
  }

  def extractTaskDetails: PartialFunction[TaskDetails, TaskData] = {
    case TaskDetails(id, name, startTime, interval, lastRunDate, lastRunStatus) =>
      (id, name, startTime, interval.toJava, lastRunDate, lastRunStatus)
  }

  override def * : ProvenShape[TaskDetails] =
    (id, name, startTime, interval, lastRunDate, lastRunStatus) <> (constructTaskDetails, extractTaskDetails.lift)

}

object TaskDetailsTable {
  val table = TableQuery[TaskDetailsTable]
}
