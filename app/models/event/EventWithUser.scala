package models.event
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class EventWithUser(event: Event, user: Option[EventUser])

object EventWithUser {
  implicit val ReportUserEventFormat: OFormat[EventWithUser] = Json.format[EventWithUser]
}
