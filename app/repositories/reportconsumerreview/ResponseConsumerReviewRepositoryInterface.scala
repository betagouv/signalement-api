package repositories.reportconsumerreview

import com.google.inject.ImplementedBy
import models.report.review.ResponseConsumerReview
import models.report.review.ResponseConsumerReviewId
import repositories.TypedCRUDRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

@ImplementedBy(classOf[ResponseConsumerReviewRepository])
trait ResponseConsumerReviewRepositoryInterface
    extends TypedCRUDRepositoryInterface[ResponseConsumerReview, ResponseConsumerReviewId] {

  def findByReportId(reportId: UUID): Future[List[ResponseConsumerReview]]

  def findByCompany(companyId: Option[UUID]): Future[List[ResponseConsumerReview]]
}
