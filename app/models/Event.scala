package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Event(
                 id: UUID,
                 reportId: UUID,
                 userId: UUID,
                 creationDate: LocalDateTime,
                 eventType: String,
                 action: String,
                 resultAction: Option[String],
                 detail: Option[String]
                 )
                 
object Event {

  implicit val eventFormat: OFormat[Event] = Json.format[Event]

}