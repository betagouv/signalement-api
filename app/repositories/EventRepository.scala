package repositories

import cats.data.NonEmptyList
import models._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import utils.Constants
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ActionEvent.REPORT_REVIEW_ON_RESPONSE
import utils.Constants.EventType.EventTypeValue
import utils.Constants.EventType.PRO
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.time.Duration
import slick.jdbc.JdbcProfile

import java.sql.Timestamp
import java.time.format.DateTimeFormatter

case class EventFilter(eventType: Option[EventTypeValue] = None, action: Option[ActionEventValue] = None)

class EventTables(tag: Tag) extends Table[Event](tag, "events") {

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
    case (id, reportId, companyId, userId, creationDate, eventType, action, details) =>
      Event(
        Some(id),
        reportId,
        companyId,
        userId,
        Some(creationDate),
        Constants.EventType.fromValue(eventType),
        Constants.ActionEvent.fromValue(action),
        details
      )
  }

  def extractEvent: PartialFunction[Event, EventData] = {
    case Event(id, reportId, companyId, userId, creationDate, eventType, action, details) =>
      (id.get, reportId, companyId, userId, creationDate.get, eventType.value, action.value, details)
  }

  def * =
    (id, reportId, companyId, userId, creationDate, eventType, action, details) <> (constructEvent, extractEvent.lift)
}

object EventTables {
  val tables = TableQuery[EventTables]
}

@Singleton
class EventRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  import dbConfig._

  val userTableQuery = UserTables.tables

  val eventTableQuery = EventTables.tables

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
      .filterOpt(filter.eventType) { case (table, eventType) =>
        table.eventType === eventType.value
      }
      .filterOpt(filter.action) { case (table, action) =>
        table.action === action.value
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
      .joinLeft(userTableQuery)
      .on(_.userId === _.id)
      .sortBy(_._1.creationDate.desc)
      .to[List]
      .result
  }

  def getCompanyEventsWithUsers(companyId: UUID, filter: EventFilter): Future[List[(Event, Option[User])]] = db.run {
    getRawEvents(filter)
      .filter(_.companyId === companyId)
      .filter(!_.reportId.isDefined)
      .joinLeft(userTableQuery)
      .on(_.userId === _.id)
      .sortBy(_._1.creationDate.desc)
      .to[List]
      .result
  }

  def getReportResponseReviews(companyId: Option[UUID]): Future[Seq[Event]] =
    db.run(
      eventTableQuery
        .filter(_.action === REPORT_REVIEW_ON_RESPONSE.value)
        .joinLeft(ReportTables.tables)
        .on(_.reportId === _.id)
        .filterOpt(companyId) { case (table, id) =>
          val res1 = table._1.companyId === id
          val res2 = table._2.map(_.companyId === id).flatten
          res1 || res2
        }
        .map(_._1)
        .result
    )

  def prefetchReportsEvents(reports: List[Report]): Future[Map[UUID, List[Event]]] = {
    val reportsIds = reports.map(_.id)
    db.run(
      eventTableQuery
        .filter(
          _.reportId inSetBind reportsIds
        )
        .to[List]
        .result
    ).map(events => events.groupBy(_.reportId.get))
  }

  def fetchEvents(companyIds: List[UUID]): Future[Map[UUID, List[Event]]] =
    db.run(
      eventTableQuery
        .filter(_.companyId inSetBind companyIds.distinct)
        .sortBy(_.creationDate.desc.nullsLast)
        .to[List]
        .result
    ).map(f => f.groupBy(_.companyId.get).toMap)

  def getAvgTimeUntilEvent(action: ActionEventValue, companyId: Option[UUID] = None): Future[Option[Duration]] =
    db.run(
      ReportTables.tables
        .filterOpt(companyId) { case (table, companyId) =>
          table.companyId === companyId
        }
        .join(eventTableQuery)
        .on(_.id === _.reportId)
        .filter(_._2.action === action.value)
        .map(x => x._2.creationDate - x._1.creationDate)
        .avg
        .result
    )

  def getReportCountHavingEvent(action: ActionEventValue, companyId: Option[UUID] = None): Future[Int] =
    db.run(
      ReportTables.tables
        .filterOpt(companyId) { case (table, companyId) =>
          table.companyId === companyId
        }
        .join(eventTableQuery)
        .on(_.id === _.reportId)
        .filter(_._2.action === action.value)
        .length
        .result
    )

  def getProReportStat(
      ticks: Int,
      startingDate: OffsetDateTime,
      actions: NonEmptyList[ActionEventValue]
  ): Future[Vector[(Timestamp, Int)]] =
    db.run(
      sql"""select * from (select my_date_trunc('month'::text, creation_date)::timestamp, count(distinct report_id)
  from events
    where event_type = '#${PRO.value}'
    and report_id is not null
    and action in (#${actions.toList.map(_.value).mkString("'", "','", "'")}) 
and creation_date >= '#${dateTimeFormatter.format(startingDate)}'::timestamp
  group by  my_date_trunc('month'::text,creation_date)
  order by  1 DESC LIMIT #${ticks} ) as res order by 1 ASC""".as[(Timestamp, Int)]
    )

  def getProReportResponseStat(
      ticks: Int,
      startingDate: OffsetDateTime,
      responseTypes: NonEmptyList[ReportResponseType]
  ): Future[Vector[(Timestamp, Int)]] =
    db.run(
      sql"""select * from (select my_date_trunc('month'::text, creation_date)::timestamp, count(distinct report_id)
  from events
    where event_type = '#${PRO.value}'
    and report_id is not null
    and action = '#${REPORT_PRO_RESPONSE.value}'
    and  (details->>'responseType')::varchar in (#${responseTypes.toList.map(_.toString).mkString("'", "','", "'")})
and creation_date >= '#${dateTimeFormatter.format(startingDate)}'::timestamp
  group by  my_date_trunc('month'::text,creation_date)
  order by  1 DESC LIMIT #${ticks} ) as res order by 1 ASC""".as[(Timestamp, Int)]
    )

}
