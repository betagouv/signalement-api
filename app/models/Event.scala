package models

import java.time.{LocalDateTime}
import java.util.UUID

import play.api.libs.json.{JsString, Json, Writes}
import utils.Constants.{ActionEvent}
import utils.Constants.EventType.EventTypeValues


case class Event(
                 id: UUID,
                 reportId: UUID,
                 userId: UUID,
                 creationDate: LocalDateTime,
                 eventType: EventTypeValues,
                 action: ActionEvent,
                 resultAction: Option[String],
                 detail: Option[String]
                )
                 
object Event {

  implicit val eventWrites = new Writes[Event] {

    def writes(event: Event) = Json.obj(
      "id" -> event.id,
      "reportId" -> event.reportId,
      "userId" -> event.userId,
      "creationDate" -> event.creationDate,
      "eventType" -> event.eventType.value,
      "action" -> event.action.value,
      "resultAction" -> event.resultAction,
      "detail" -> event.detail
    )
  }

}