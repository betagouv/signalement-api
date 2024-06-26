package repositories.tasklock

import repositories.PostgresProfile.api._
import repositories.TypedDatabaseTable
import slick.lifted.ProvenShape

import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import scala.jdk.DurationConverters._

class TaskDetailsTable(tag: Tag) extends TypedDatabaseTable[TaskDetails, Int](tag, "task_details") {

  def name         = column[String]("name")
  def startTime    = column[Option[LocalTime]]("start_time")
  def interval     = column[Duration]("interval")
  def lastRunDate  = column[OffsetDateTime]("last_run_date")
  def lastRunError = column[Option[String]]("last_run_error")

  type TaskData = (Int, String, Option[LocalTime], Duration, OffsetDateTime, Option[String])

  def constructTaskDetails: TaskData => TaskDetails = {
    case (id, name, startTime, interval, lastRunDate, lastRunError) =>
      TaskDetails(id, name, startTime, interval.toScala, lastRunDate, lastRunError)
  }

  def extractTaskDetails: PartialFunction[TaskDetails, TaskData] = {
    case TaskDetails(id, name, startTime, interval, lastRunDate, lastRunError) =>
      (id, name, startTime, interval.toJava, lastRunDate, lastRunError)
  }

  override def * : ProvenShape[TaskDetails] =
    (id, name, startTime, interval, lastRunDate, lastRunError) <> (constructTaskDetails, extractTaskDetails.lift)

}

object TaskDetailsTable {
  val table = TableQuery[TaskDetailsTable]
}
