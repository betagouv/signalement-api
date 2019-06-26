package repositories

import java.time.LocalDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.Event
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.Constants
import utils.Constants.EventType.EventTypeValue

import scala.concurrent.{ExecutionContext, Future}

case class EventFilter(eventType: Option[EventTypeValue])

@Singleton
class EventRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, reportRepository: ReportRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  private class EventTable(tag: Tag) extends Table[Event](tag, "events") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[UUID]("report_id")
    def userId = column[UUID]("user_id")
    def creationDate = column[LocalDateTime]("creation_date")
    def eventType = column[String]("event_type")
    def action = column[String]("action")
    def resultAction = column[Option[Boolean]]("result_action")
    def detail = column[Option[String]]("detail")
    def report = foreignKey("fk_events_report", reportId, reportTableQuery)(_.id)

    type EventData = (UUID, UUID, UUID, LocalDateTime, String, String, Option[Boolean], Option[String])

    def constructEvent: EventData => Event = {

      case (id, reportId, userId, creationDate, eventType, action, resultAction, detail) => {
        Event(Some(id), Some(reportId), userId, Some(creationDate), Constants.EventType.fromValue(eventType).get,
          Constants.ActionEvent.fromValue(action).get, resultAction, detail)
      }
    }

    def extractEvent: PartialFunction[Event, EventData] = {
      case Event(id, reportId, userId, creationDate, eventType, action, resultAction, detail) =>
        (id.get, reportId.get, userId, creationDate.get, eventType.value, action.value, resultAction, detail)
    }

    def * =
      (id, reportId, userId, creationDate, eventType, action, resultAction, detail) <> (constructEvent, extractEvent.lift)
  }

  private val reportTableQuery = TableQuery[reportRepository.ReportTable]

  private val eventTableQuery = TableQuery[EventTable]

  private val date_part = SimpleFunction.binary[String, LocalDateTime, Int]("date_part")

  def createEvent(event: Event): Future[Event] = db
    .run(eventTableQuery += event)
    .map(_ => event)

  def deleteEvents(uuidReport: UUID): Future[Int] = db
    .run(
      eventTableQuery
        .filter(_.reportId === uuidReport)
        .delete
    )

  def getEvents(uuidReport: UUID, filter: EventFilter): Future[List[Event]] = db.run {
    eventTableQuery
      .filter(_.reportId === uuidReport)
      .filterOpt(filter.eventType) {
        case (table, eventType) => table.eventType === eventType.value
      }
      .sortBy(_.creationDate.desc)
      .to[List]
      .result
  }

}

