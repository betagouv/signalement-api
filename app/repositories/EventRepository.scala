package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import slick.jdbc.JdbcProfile
import utils.Constants
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType.EventTypeValue

import scala.concurrent.{ExecutionContext, Future}

case class EventFilter(eventType: Option[EventTypeValue] = None, action: Option[ActionEventValue] = None)

@Singleton
class EventRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, val userRepository: UserRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  class EventTable(tag: Tag) extends Table[Event](tag, "events") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("report_id")
    def companyId = column[Option[UUID]]("company_id")
    def userId = column[Option[UUID]]("user_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def eventType = column[String]("event_type")
    def action = column[String]("action")
    def details = column[JsValue]("details")

    type EventData = (UUID, Option[UUID], Option[UUID], Option[UUID], OffsetDateTime, String, String, JsValue)

    def constructEvent: EventData => Event = {
      case (id, reportId, companyId, userId, creationDate, eventType, action, details) => {
        Event(Some(id), reportId, companyId, userId, Some(creationDate), Constants.EventType.fromValue(eventType),
          Constants.ActionEvent.fromValue(action), details)
      }
    }

    def extractEvent: PartialFunction[Event, EventData] = {
      case Event(id, reportId, companyId, userId, creationDate, eventType, action, details) =>
        (id.get, reportId, companyId, userId, creationDate.get, eventType.value, action.value, details)
    }

    def * =
      (id, reportId, companyId, userId, creationDate, eventType, action, details) <> (constructEvent, extractEvent.lift)
  }

  val userTableQuery = TableQuery[userRepository.UserTable]

  val eventTableQuery = TableQuery[EventTable]
  
  def list: Future[Seq[Event]] = db.run(eventTableQuery.result)

  def createEvent(event: Event): Future[Event] = db
    .run(eventTableQuery += event)
    .map(_ => event)

  def deleteEvents(uuidReport: UUID): Future[Int] = db
    .run(
      eventTableQuery
        .filter(_.reportId === uuidReport)
        .delete
    )

  private def getRawEvents(filter: EventFilter) =
    eventTableQuery
      .filterOpt(filter.eventType) {
        case (table, eventType) => table.eventType === eventType.value
      }
      .filterOpt(filter.action) {
        case (table, action) => table.action === action.value
      }

  def getEvents(reportId: UUID, filter: EventFilter): Future[List[Event]] = db.run {
    getRawEvents(filter)
      .filter(_.reportId === reportId)
      .sortBy(_.creationDate.desc)
      .to[List]
      .result
  }

  def getEventsWithUsers(reportId: UUID, filter: EventFilter): Future[List[(Event, Option[User])]] = db.run {
    getRawEvents(filter)
      .filter(_.reportId === reportId)
      .joinLeft(userTableQuery).on(_.userId === _.id)
      .sortBy(_._1.creationDate.desc)
      .to[List]
      .result
  }

  def getCompanyEventsWithUsers(companyId: UUID, filter: EventFilter): Future[List[(Event, Option[User])]] = db.run {
    getRawEvents(filter)
      .filter(_.companyId === companyId)
      .filter(!_.reportId.isDefined)
      .joinLeft(userTableQuery).on(_.userId === _.id)
      .sortBy(_._1.creationDate.desc)
      .to[List]
      .result
  }

  def prefetchReportsEvents(reports: List[Report]): Future[Map[UUID, List[Event]]] = {
    val reportsIds = reports.map(_.id)
    db.run(eventTableQuery.filter(
      _.reportId inSetBind reportsIds
    ).to[List].result)
    .map(events =>
      events.groupBy(_.reportId.get)
    )
  }

  def fetchEvents(companyIds: List[UUID]): Future[Map[UUID, List[Event]]] = {
    db.run(eventTableQuery
      .filter(_.companyId inSetBind companyIds.distinct)
      .sortBy(_.creationDate.desc.nullsLast)
      .to[List].result
    )
      .map(f => f.groupBy(_.companyId.get).toMap)
  }
}
