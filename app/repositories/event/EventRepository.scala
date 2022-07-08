package repositories.event

import cats.data.NonEmptyList
import models._
import models.event.Event
import models.report.Report
import models.report.ReportResponseType
import models.report.ReportStatus
import models.report.ReportTag
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import repositories.report.ReportColumnType._
import repositories.report.ReportTable
import repositories.user.UserTable
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
import utils.Constants.ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ActionEvent.REPORT_REVIEW_ON_RESPONSE
import utils.Constants.EventType.PRO

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EventRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[EventTable, Event]
    with EventRepositoryInterface {

  override val table: TableQuery[EventTable] = EventTable.table

  val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  import dbConfig._

  override def deleteByUserId(userId: UUID): Future[Int] = db
    .run(
      table
        .filter(_.userId === userId)
        .delete
    )

  override def deleteByReportId(uuidReport: UUID): Future[Int] = db
    .run(
      table
        .filter(_.reportId === uuidReport)
        .delete
    )

  private def getRawEvents(filter: EventFilter) =
    table
      .filterOpt(filter.eventType) { case (table, eventType) =>
        table.eventType === eventType.value
      }
      .filterOpt(filter.action) { case (table, action) =>
        table.action === action.value
      }

  override def getEvents(reportId: UUID, filter: EventFilter): Future[List[Event]] = db.run {
    getRawEvents(filter)
      .filter(_.reportId === reportId)
      .sortBy(_.creationDate.desc)
      .to[List]
      .result
  }

  override def getEventsWithUsers(reportId: UUID, filter: EventFilter): Future[List[(Event, Option[User])]] = db.run {
    getRawEvents(filter)
      .filter(_.reportId === reportId)
      .joinLeft(UserTable.table)
      .on(_.userId === _.id)
      .sortBy(_._1.creationDate.desc)
      .to[List]
      .result
  }

  override def getCompanyEventsWithUsers(companyId: UUID, filter: EventFilter): Future[List[(Event, Option[User])]] =
    db.run {
      getRawEvents(filter)
        .filter(_.companyId === companyId)
        .filter(!_.reportId.isDefined)
        .joinLeft(UserTable.table)
        .on(_.userId === _.id)
        .sortBy(_._1.creationDate.desc)
        .to[List]
        .result
    }

  override def getReportResponseReviews(companyId: Option[UUID]): Future[Seq[Event]] =
    db.run(
      table
        .filter(_.action === REPORT_REVIEW_ON_RESPONSE.value)
        .joinLeft(ReportTable.table)
        .on(_.reportId === _.id)
        .filterOpt(companyId) { case (table, id) =>
          val res1 = table._1.companyId === id
          val res2 = table._2.map(_.companyId === id).flatten
          res1 || res2
        }
        .map(_._1)
        .result
    )

  override def prefetchReportsEvents(reports: List[Report]): Future[Map[UUID, List[Event]]] = {
    val reportsIds = reports.map(_.id)
    db.run(
      table
        .filter(
          _.reportId inSetBind reportsIds
        )
        .to[List]
        .result
    ).map(events => events.groupBy(_.reportId.get))
  }

  override def fetchEvents(companyIds: List[UUID]): Future[Map[UUID, List[Event]]] =
    db.run(
      table
        .filter(_.companyId inSetBind companyIds.distinct)
        .sortBy(_.creationDate.desc.nullsLast)
        .to[List]
        .result
    ).map(f => f.groupBy(_.companyId.get).toMap)

  override def getAvgTimeUntilEvent(
      action: ActionEventValue,
      companyId: Option[UUID] = None,
      status: Seq[ReportStatus] = Seq.empty,
      withoutTags: Seq[ReportTag] = Seq.empty
  ): Future[Option[Duration]] =
    db.run(
      ReportTable.table
        .filterOpt(companyId) { case (table, companyId) =>
          table.companyId === companyId
        }
        .filterIf(status.nonEmpty) { case table =>
          table.status.inSet(status.map(_.entryName))
        }
        .filterNot { table =>
          table.tags @& withoutTags.toList.bind
        }
        .join(table)
        .on(_.id === _.reportId)
        .filter(_._2.action === action.value)
        .map(x => x._2.creationDate - x._1.creationDate)
        .avg
        .result
    )

  override def getReportCountHavingEvent(action: ActionEventValue, companyId: Option[UUID] = None): Future[Int] =
    db.run(
      ReportTable.table
        .filterOpt(companyId) { case (table, companyId) =>
          table.companyId === companyId
        }
        .join(table)
        .on(_.id === _.reportId)
        .filter(_._2.action === action.value)
        .length
        .result
    )

  override def getMonthlyReportsTransmittedToProStat(
      start: OffsetDateTime
  ): Future[Vector[(Timestamp, Int)]] = {
    val startStr = dateFormatter.format(start)
    db.run(sql"""
WITH reports_sent_by_courrier AS (
	WITH companies_courrier_sent AS (
		SELECT DISTINCT ON (company_id)
			company_id,
			creation_date
		FROM events
		WHERE action = ${POST_ACCOUNT_ACTIVATION_DOC.value}
		AND creation_date >= '#$startStr'
		AND company_id IS NOT NULL
		ORDER BY company_id ASC, creation_date ASC
	)
	SELECT
		reports.id AS report_id,
		companies_courrier_sent.creation_date
	FROM companies_courrier_sent
	JOIN reports
	ON reports.company_id = companies_courrier_sent.company_id
	WHERE reports.creation_date < companies_courrier_sent.creation_date
), reports_sent_by_email AS (
	SELECT DISTINCT ON (report_id)
		report_id,
		creation_date
	FROM events
  WHERE action = ${EMAIL_PRO_NEW_REPORT.value}
	AND creation_date >= '#$startStr'
	AND report_id IS NOT NULL
	ORDER BY report_id ASC, creation_date ASC
)
SELECT
	DATE_TRUNC('month', LEAST (reports_sent_by_courrier.creation_date, reports_sent_by_email.creation_date))::timestamp AS transmission_month,
	count(*) as cpt
FROM reports_sent_by_courrier
    FULL JOIN reports_sent_by_email
ON reports_sent_by_courrier.report_id = reports_sent_by_email.report_id
GROUP BY transmission_month
ORDER by transmission_month
""".as[(Timestamp, Int)])
  }

  override def getProReportResponseStat(
      ticks: Int,
      startingDate: OffsetDateTime,
      responseTypes: NonEmptyList[ReportResponseType]
  ): Future[Vector[(Timestamp, Int)]] =
    db.run(
      sql"""select
    my_date_trunc('month'::text, creation_date)::timestamp as creation_month,
    count(distinct report_id)
  from events
    where event_type = '#${PRO.value}'
    and report_id is not null
    and action = '#${REPORT_PRO_RESPONSE.value}'
    and  (details->>'responseType')::varchar in (#${responseTypes.toList.map(_.toString).mkString("'", "','", "'")})
    and creation_date >= '#${dateFormatter.format(startingDate)}'::timestamp
  group by creation_month
  order by 1 ASC LIMIT #${ticks} """.as[(Timestamp, Int)]
    )

}
