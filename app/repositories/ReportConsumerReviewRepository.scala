package repositories

import models.report.review.ResponseEvaluation
import models.report.review.ResponseConsumerReview
import models.report.review.ResponseConsumerReviewId
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import slick.jdbc.JdbcProfile

import java.time.format.DateTimeFormatter
import repositories.mapping.ResponseConsumerReview._

class ResponseConsumerReviewTables(tag: Tag) extends Table[ResponseConsumerReview](tag, "report_consumer_review") {

  def id = column[ResponseConsumerReviewId]("id", O.PrimaryKey)
  def reportId = column[UUID]("report_id")
  def creationDate = column[OffsetDateTime]("creation_date")
  def evaluation = column[ResponseEvaluation]("evaluation")
  def details = column[Option[String]]("details")

  type ResponseConsumerReviewData = (
      ResponseConsumerReviewId,
      UUID,
      ResponseEvaluation,
      OffsetDateTime,
      Option[String]
  )

  def constructResponseConsumerReview: ResponseConsumerReviewData => ResponseConsumerReview = {
    case (id, reportId, evaluation, creationDate, details) =>
      ResponseConsumerReview(id, reportId, evaluation, creationDate, details)
  }

  def extractResponseConsumerReview: PartialFunction[ResponseConsumerReview, ResponseConsumerReviewData] = {
    case ResponseConsumerReview(id, reportId, evaluation, creationDate, details) =>
      (id, reportId, evaluation, creationDate, details)
  }

  def * =
    (
      id,
      reportId,
      evaluation,
      creationDate,
      details
    ) <> (constructResponseConsumerReview, extractResponseConsumerReview.lift)
}

object ResponseConsumerReviewTables {
  val tables = TableQuery[ResponseConsumerReviewTables]
}

@Singleton
class ResponseConsumerReviewRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  import dbConfig._

  val responseConsumerReviewTableQuery = ResponseConsumerReviewTables.tables

  def create(responseConsumerReview: ResponseConsumerReview): Future[ResponseConsumerReview] = db
    .run(responseConsumerReviewTableQuery += responseConsumerReview)
    .map(_ => responseConsumerReview)

  def delete(id: ResponseConsumerReviewId): Future[Int] = db
    .run(responseConsumerReviewTableQuery.filter(_.id === id).delete)

  def find(reportId: UUID): Future[List[ResponseConsumerReview]] =
    db.run(responseConsumerReviewTableQuery.filter(_.reportId === reportId).to[List].result)

  def findByCompany(companyId: Option[UUID]): Future[List[ResponseConsumerReview]] =
    db.run(
      responseConsumerReviewTableQuery
        .join(ReportTables.tables)
        .on(_.reportId === _.id)
        .filterOpt(companyId) { case (table, id) =>
          table._2.companyId === id
        }
        .map(_._1)
        .to[List]
        .result
    )

}
