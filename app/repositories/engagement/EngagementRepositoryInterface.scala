package repositories.engagement

import models.UserRole
import models.event.Event
import models.engagement.Engagement
import models.engagement.EngagementId
import models.report.Report
import repositories.TypedCRUDRepositoryInterface

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future

trait EngagementRepositoryInterface extends TypedCRUDRepositoryInterface[Engagement, EngagementId] {
  def listEngagementsWithEventsAndReport(
      userRole: Option[UserRole],
      companyIds: List[UUID]
  ): Future[Seq[(((Report, Engagement), Event), Option[Event])]]
  def check(promiseId: EngagementId, resolutionEventId: UUID): Future[Int]
  def uncheck(promiseId: EngagementId): Future[Int]
  def listEngagementsExpiringAt(date: LocalDate): Future[Seq[(((Engagement, Report), Event), Option[Event])]]
}
