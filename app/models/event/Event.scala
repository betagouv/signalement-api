package models.event

import play.api.libs.json._
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType

import java.time.OffsetDateTime
import java.util.UUID

case class Event(
    id: UUID,
    reportId: Option[UUID],
    companyId: Option[UUID],
    userId: Option[UUID],
    creationDate: OffsetDateTime,
    eventType: EventType,
    action: ActionEventValue,
    details: JsValue = Json.obj()
) {

  def formattedDate = this.creationDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy à HH:mm:ss"))
  def getDescription =
    this.details.as[JsObject].value.getOrElse("description", JsString("")).toString.replaceAll("^\"|\"$", "");
}

object Event {

  implicit val eventFormat: OFormat[Event]           = Json.format[Event]
  def stringToDetailsJsValue(value: String): JsValue = Json.obj("description" -> value)
}
