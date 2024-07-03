package repositories.tasklock

import play.api.libs.json.Format
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import java.time.LocalTime
import java.time.OffsetDateTime
import scala.concurrent.duration._

case class TaskDetails(
    id: Int,
    name: String,
    startTime: Option[LocalTime],
    interval: FiniteDuration,
    lastRunDate: OffsetDateTime,
    lastRunError: Option[String]
)

object TaskDetails {
  implicit private val finiteDurationFormat: Format[FiniteDuration] = new Format[FiniteDuration] {
    def reads(json: JsValue): JsResult[FiniteDuration] = LongReads.reads(json).map(_.milliseconds)

    def writes(o: FiniteDuration): JsValue = LongWrites.writes(o.toMillis)
  }

  implicit val writes: OWrites[TaskDetails] = Json.writes[TaskDetails]
}
