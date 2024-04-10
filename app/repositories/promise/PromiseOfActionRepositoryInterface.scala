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
  ): Future[Seq[((Report, PromiseOfAction), Event)]]
  def honour(promiseId: PromiseOfActionId, resolutionEventId: UUID): Future[Int]
}
