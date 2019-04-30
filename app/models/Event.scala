package models

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json._
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType.EventTypeValue


case class Event(
                  id: Option[UUID],
                  reportId: UUID,
                  userId: UUID,
                  creationDate: Option[LocalDateTime],
                  eventType: EventTypeValue,
                  action: ActionEventValue,
                  resultAction: Option[String],
                  detail: Option[String]
                )
                 
object Event {

  implicit val eventFormat: OFormat[Event] = Json.format[Event]

}