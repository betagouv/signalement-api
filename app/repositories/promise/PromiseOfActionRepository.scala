package repositories.promise

import models.UserRole
import models.event.Event
import models.promise.PromiseOfAction
import models.promise.PromiseOfActionId
import models.report.Report
import repositories.PostgresProfile.api._
import repositories.TypedCRUDRepository
import repositories.event.EventTable
import repositories.report.ReportTable
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PromiseOfActionRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[PromiseOfActionTable, PromiseOfAction, PromiseOfActionId]
    with PromiseOfActionRepositoryInterface {

  override val table = PromiseOfActionTable.table

  import dbConfig._

  def listPromisesWithEventsAndReport(
      userRole: Option[UserRole],
      companyIds: List[UUID]
  ): Future[Seq[(((Report, PromiseOfAction), Event), Option[Event])]] = db.run(
    ReportTable
      .table(userRole)
      .filter(_.companyId inSetBind companyIds)
      .join(table)
      .on { case (report, promise) => report.id === promise.reportId }
      .join(EventTable.table)
      .on { case ((_, promise), event) => promise.promiseEventId === event.id }
      .joinLeft(EventTable.table)
      .on { case (((_, promise), _), resolutionEvent) => promise.resolutionEventId === resolutionEvent.id }
      .result
  )

  override def check(promiseId: PromiseOfActionId, resolutionEventId: UUID): Future[Int] = db.run(
    table.filter(_.id === promiseId).map(_.resolutionEventId).update(Some(resolutionEventId))
  )

  override def uncheck(promiseId: PromiseOfActionId): Future[Int] = db.run(
    table.filter(_.id === promiseId).map(_.resolutionEventId).update(None)
  )
}
