package repositories.reportconsumerreview

import models.report.review.ResponseConsumerReview
import models.report.review.ResponseConsumerReviewId
import play.api.db.slick.DatabaseConfigProvider
import repositories.TypedCRUDRepository
import repositories.PostgresProfile.api._
import repositories.report.ReportTable
import repositories.reportconsumerreview.ResponseConsumerReviewColumnType._
import slick.jdbc.JdbcProfile

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ResponseConsumerReviewRepository @Inject(
    dbConfigProvider: DatabaseConfigProvider
)(implicit override val ec: ExecutionContext)
    extends TypedCRUDRepository[ResponseConsumerReviewTable, ResponseConsumerReview, ResponseConsumerReviewId]
    with ResponseConsumerReviewRepositoryInterface {

  override val dbConfig = dbConfigProvider.get[JdbcProfile]
  override val table: TableQuery[ResponseConsumerReviewTable] = ResponseConsumerReviewTable.table
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  import dbConfig._

  override def findByReportId(reportId: UUID): Future[List[ResponseConsumerReview]] =
    db.run(table.filter(_.reportId === reportId).to[List].result)

  override def findByCompany(companyId: Option[UUID]): Future[List[ResponseConsumerReview]] = db.run(
    table
      .join(ReportTable.table)
      .on(_.reportId === _.id)
      .filterOpt(companyId) { case (table, id) =>
        table._2.companyId === id
      }
      .map(_._1)
      .to[List]
      .result
  )

}
