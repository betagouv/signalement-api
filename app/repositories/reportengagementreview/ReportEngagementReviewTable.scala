package repositories.reportengagementreview

import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReviewId
import models.report.review.ResponseEvaluation
import repositories.TypedDatabaseTable
import slick.lifted.ProvenShape
import slick.lifted.Tag
import repositories.PostgresProfile.api._
import repositories.reportconsumerreview.ResponseConsumerReviewColumnType._

import java.time.OffsetDateTime
import java.util.UUID

class ReportEngagementReviewTable(tag: Tag)
    extends TypedDatabaseTable[EngagementReview, ResponseConsumerReviewId](tag, "engagement_reviews") {

  def reportId     = column[UUID]("report_id")
  def creationDate = column[OffsetDateTime]("creation_date")
  def evaluation   = column[ResponseEvaluation]("evaluation")
  def details      = column[Option[String]]("details")

  type EngagementReviewData = (
      ResponseConsumerReviewId,
      UUID,
      ResponseEvaluation,
      OffsetDateTime,
      Option[String]
  )

  def constructResponseConsumerReview: EngagementReviewData => EngagementReview = {
    case (id, reportId, evaluation, creationDate, details) =>
      EngagementReview(id, reportId, evaluation, creationDate, details)
  }

  def extractResponseConsumerReview: PartialFunction[EngagementReview, EngagementReviewData] = {
    case EngagementReview(id, reportId, evaluation, creationDate, details) =>
      (id, reportId, evaluation, creationDate, details)
  }

  override def * : ProvenShape[EngagementReview] =
    (
      id,
      reportId,
      evaluation,
      creationDate,
      details
    ) <> (constructResponseConsumerReview, extractResponseConsumerReview.lift)
}

object ReportEngagementReviewTable {
  val table = TableQuery[ReportEngagementReviewTable]
}
