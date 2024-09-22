package repositories.engagement

import models.UserRole
import models.event.Event
import models.engagement.Engagement
import models.engagement.EngagementId
import models.report.Report
import repositories.PostgresProfile.api._
import repositories.TypedCRUDRepository
import repositories.event.EventTable
import repositories.report.ReportTable
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EngagementRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[EngagementTable, Engagement, EngagementId]
    with EngagementRepositoryInterface {

  override val table = EngagementTable.table

  import dbConfig._

  def listEngagementsWithEventsAndReport(
      userRole: Option[UserRole],
      companyIds: List[UUID]
  ): Future[Seq[(((Report, Engagement), Event), Option[Event])]] = db.run(
    ReportTable
      .table(userRole)
      .filter(_.companyId inSetBind companyIds)
      .join(table)
      .on { case (report, engagement) => report.id === engagement.reportId }
      .join(EventTable.table)
      .on { case ((_, engagement), promiseEvent) => engagement.promiseEventId === promiseEvent.id }
      .joinLeft(EventTable.table)
      .on { case (((_, engagement), _), resolutionEvent) => engagement.resolutionEventId === resolutionEvent.id }
      .result
  )

  override def check(engagementId: EngagementId, resolutionEventId: UUID): Future[Int] = db.run(
    table.filter(_.id === engagementId).map(_.resolutionEventId).update(Some(resolutionEventId))
  )

  override def uncheck(engagementId: EngagementId): Future[Int] = db.run(
    table.filter(_.id === engagementId).map(_.resolutionEventId).update(None)
  )

  override def listEngagementsExpiringAt(
      date: LocalDate
  ): Future[Seq[(((Engagement, Report), Event), Option[Event])]] =
    db.run(
      table
        .filter(_.expirationDate >= ZonedDateTime.of(date, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime)
        .filter(_.expirationDate <= ZonedDateTime.of(date, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime)
        .join(ReportTable.table)
        .on { case (engagement, report) => engagement.reportId === report.id }
        .join(EventTable.table)
        .on { case ((engagement, _), event) => engagement.promiseEventId === event.id }
        .joinLeft(EventTable.table)
        .on { case (((engagement, _), _), resolutionEvent) => engagement.resolutionEventId === resolutionEvent.id }
        .result
    )

  override def remove(reportId: UUID): Future[Int] = db.run(
    table
      .filter(_.reportId === reportId)
      .delete
  )
}
