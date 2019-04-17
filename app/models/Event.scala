package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Event(
                 id: UUID,
                 reportId: Option[UUID],
                 userId: Option[UUID],
                 creationDate: LocalDateTime,
                 eventType: String,
                 action: String,
                 resultAction: Option[Boolean],
                 detail: Option[String]
                 )
                 
object Event {

  implicit val eventFormat: OFormat[Event] = Json.format[Event]

}