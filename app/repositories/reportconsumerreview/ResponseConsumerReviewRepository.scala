package repositories.reportconsumerreview

import models.report.review.ResponseConsumerReview
import models.report.review.ResponseConsumerReviewId
import play.api.db.slick.DatabaseConfigProvider
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
class ResponseConsumerReviewRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  import dbConfig._

  def create(responseConsumerReview: ResponseConsumerReview): Future[ResponseConsumerReview] = db
    .run(ResponseConsumerReviewTable.table += responseConsumerReview)
    .map(_ => responseConsumerReview)

  def delete(id: ResponseConsumerReviewId): Future[Int] = db
    .run(ResponseConsumerReviewTable.table.filter(_.id === id).delete)

  def find(reportId: UUID): Future[List[ResponseConsumerReview]] =
    db.run(ResponseConsumerReviewTable.table.filter(_.reportId === reportId).to[List].result)

  def findByCompany(companyId: Option[UUID]): Future[List[ResponseConsumerReview]] =
    db.run(
      ResponseConsumerReviewTable.table
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
