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

  override def listPromisesWithEventsAndReport(
      userRole: Option[UserRole],
      companyIds: List[UUID]
  ): Future[Seq[((Report, PromiseOfAction), Event)]] = db.run(
    ReportTable
      .table(userRole)
      .filter(_.companyId inSetBind companyIds)
      .join(table)
      .on { case (report, promise) => report.id === promise.reportId }
      .filter { case (_, promise) => promise.resolutionEventId.isEmpty }
      .join(EventTable.table)
      .on { case ((_, promise), event) => promise.promiseEventId === event.id }
      .result
  )

  override def honour(promiseId: PromiseOfActionId, resolutionEventId: UUID): Future[Int] = db.run(
    table.filter(_.id === promiseId).map(_.resolutionEventId).update(Some(resolutionEventId))
  )
}
