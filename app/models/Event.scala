package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{JsString, Json, OFormat, Writes}
import utils.Constants.Event.EventTypeValues

case class Event(
                 id: UUID,
                 reportId: UUID,
                 userId: UUID,
                 creationDate: LocalDateTime,
                 eventType: EventTypeValues,
                 action: String,
                 resultAction: Option[String],
                 detail: Option[String]
                 )
                 
object Event {

  implicit val eventWrites = new Writes[Event] {
    implicit val eventTypeValuesWrites = new Writes[EventTypeValues] {
      def writes(eventType: EventTypeValues) = JsString(eventType.value)
    }

    def writes(event: Event) = Json.obj(
      "id" -> event.id,
      "reportId" -> event.reportId,
      "userId" -> event.userId,
      "creationDate" -> event.creationDate,
      "eventType" -> event.eventType,
      "action" -> event.action,
      "resultAction" -> event.resultAction,
      "detail" -> event.detail
    )
  }

  //implicit val eventFormat: OFormat[Event] = Json.format[Event]

}