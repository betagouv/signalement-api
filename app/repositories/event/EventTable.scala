package repositories.event

import models.event.Event
import play.api.libs.json.JsValue
import repositories.DatabaseTable
import utils.Constants

import java.time.OffsetDateTime
import java.util.UUID
import repositories.PostgresProfile.api._
import utils.Constants.ActionEvent.ACTIVATION_DOC_RETURNED

class EventTable(tag: Tag) extends DatabaseTable[Event](tag, "events") {

  def reportId     = column[Option[UUID]]("report_id")
  def companyId    = column[Option[UUID]]("company_id")
  def userId       = column[Option[UUID]]("user_id")
  def creationDate = column[OffsetDateTime]("creation_date")
  def eventType    = column[String]("event_type")
  def action       = column[String]("action")
  def details      = column[JsValue]("details")

  type EventData = (UUID, Option[UUID], Option[UUID], Option[UUID], OffsetDateTime, String, String, JsValue)

  def constructEvent: EventData => Event = {
    case (id, reportId, companyId, userId, creationDate, eventType, action, details) =>
      Event(
        id,
        reportId,
        companyId,
        userId,
        creationDate,
        Constants.EventType.withName(eventType),
        Constants.ActionEvent.fromValue(action),
        details
      )
  }

  def extractEvent: PartialFunction[Event, EventData] = {
    case Event(id, reportId, companyId, userId, creationDate, eventType, action, details) =>
      (id, reportId, companyId, userId, creationDate, eventType.entryName, action.value, details)
  }

  def * =
    (id, reportId, companyId, userId, creationDate, eventType, action, details) <> (constructEvent, extractEvent.lift)
}

object EventTable {
  val fullTableIncludingDeprecated = TableQuery[EventTable]

  val table: Query[EventTable, EventTable#TableElementType, Seq] = fullTableIncludingDeprecated
    // These events are from an old feature
    .filter(_.action =!= ACTIVATION_DOC_RETURNED.value)
}
