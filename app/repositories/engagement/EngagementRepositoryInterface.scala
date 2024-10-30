package repositories.engagement

import models.User
import models.engagement.Engagement
import models.engagement.EngagementId
import models.event.Event
import models.report.Report
import repositories.TypedCRUDRepositoryInterface

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Future

trait EngagementRepositoryInterface extends TypedCRUDRepositoryInterface[Engagement, EngagementId] {
  def listEngagementsWithEventsAndReport(
      user: Option[User],
      companyIds: List[UUID]
  ): Future[Seq[(((Report, Engagement), Event), Option[Event])]]
  def check(engagementId: EngagementId, resolutionEventId: UUID): Future[Int]
  def uncheck(engagementId: EngagementId): Future[Int]
  def remove(reportId: UUID): Future[Int]
  def listEngagementsExpiringAt(date: LocalDate): Future[Seq[(((Engagement, Report), Event), Option[Event])]]
}
