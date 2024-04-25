package repositories.reportengagementreview

import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReviewId
import repositories.TypedCRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._
import repositories.reportconsumerreview.ResponseConsumerReviewColumnType._

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportEngagementReviewRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends TypedCRUDRepository[ReportEngagementReviewTable, EngagementReview, ResponseConsumerReviewId]
    with ReportEngagementReviewRepositoryInterface {

  override val table: TableQuery[ReportEngagementReviewTable] = ReportEngagementReviewTable.table

  import dbConfig._

  override def findByReportId(reportId: UUID): Future[List[EngagementReview]] =
    db.run(table.filter(_.reportId === reportId).to[List].result)
}
