package repositories.promise

import models.UserRole
import models.event.Event
import models.promise.PromiseOfAction
import models.promise.PromiseOfActionId
import models.report.Report
import repositories.TypedCRUDRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

trait PromiseOfActionRepositoryInterface extends TypedCRUDRepositoryInterface[PromiseOfAction, PromiseOfActionId] {
  def listPromisesWithEventsAndReport(
      userRole: Option[UserRole],
      companyIds: List[UUID]
  ): Future[Seq[(((Report, PromiseOfAction), Event), Option[Event])]]
  def check(promiseId: PromiseOfActionId, resolutionEventId: UUID): Future[Int]
  def uncheck(promiseId: PromiseOfActionId): Future[Int]
}
