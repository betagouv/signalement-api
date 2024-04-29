package repositories.reportengagementreview

import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReviewId
import repositories.TypedCRUDRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

trait ReportEngagementReviewRepositoryInterface
    extends TypedCRUDRepositoryInterface[EngagementReview, ResponseConsumerReviewId] {
  def findByReportId(reportId: UUID): Future[List[EngagementReview]]

  def findByCompany(companyId: Option[UUID]): Future[List[EngagementReview]]
}
