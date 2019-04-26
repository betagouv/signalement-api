package models

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.{JsPath, JsString, Json, Reads, Writes}
import utils.Constants.ActionEvent
import utils.Constants.EventType.EventTypeValues
import play.api.libs.functional.syntax._


case class Event(
                 //id: Option[UUID],
                 id: UUID,
                 reportId: UUID,
                 userId: UUID,
                 //creationDate: Option[LocalDateTime],
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

  /*
  implicit val eventReads: Reads[Event] = {
    implicit val eventTypeValuesReads: Reads[EventTypeValues] = {

      val locationReadsBuilder = (JsPath \ "eventType").readNullable[String]

      locationReadsBuilder.apply(EventTypeValues.apply _)

    }

    ((JsPath \ "id").readNullable[UUID] and
      (JsPath \ "reportId").read[UUID] and
      (JsPath \ "userId").read[UUID] and
      (JsPath \ "creationDate").readNullable[LocalDateTime] and
      (JsPath \ "eventType").read[EventTypeValues] and
      (JsPath \ "action").read[ActionEvent] and
      (JsPath \ "resultAction").readNullable[String] and
      (JsPath \ "detail").readNullable[String]
      ) (Event.apply _)

  }
  */

}