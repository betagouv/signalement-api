package repositories.reportengagementreview

import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReviewId
import repositories.TypedCRUDRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

trait ReportEngagementReviewRepositoryInterface
    extends TypedCRUDRepositoryInterface[EngagementReview, ResponseConsumerReviewId] {
  def findByReportId(reportId: UUID): Future[Option[EngagementReview]]
  def findByReportIds(reportIds: Seq[UUID]): Future[Map[UUID, Option[EngagementReview]]]

  def findByCompany(companyId: Option[UUID]): Future[List[EngagementReview]]
}
