package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json._
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType.EventTypeValue


case class Event(
                  id: Option[UUID],
                  reportId: Option[UUID],
                  companyId: Option[UUID],
                  userId: Option[UUID],
                  creationDate: Option[OffsetDateTime],
                  eventType: EventTypeValue,
                  action: ActionEventValue,
                  details: JsValue = Json.obj()
                ) {

  def formattedDate = this.creationDate.map(_.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))).getOrElse("—")
  def getDescription = this.details.as[JsObject].value.get("description").getOrElse("")
}

object Event {

  implicit val eventFormat: OFormat[Event] = Json.format[Event]
  def stringToDetailsJsValue(value: String): JsValue = Json.obj("description" -> value)
  def jsValueToString(jsValue: Option[JsValue]) = jsValue.flatMap(_.as[JsObject].value.get("description").map(_.toString))
}