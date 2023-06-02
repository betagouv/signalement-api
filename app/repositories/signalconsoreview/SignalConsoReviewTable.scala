package repositories.signalconsoreview

import models.report.reportmetadata.Os
import models.report.signalconsoreview.SignalConsoEvaluation
import models.report.signalconsoreview.SignalConsoReview
import models.report.signalconsoreview.SignalConsoReviewId
import repositories.PostgresProfile.api._
import repositories.TypedDatabaseTable
import SignalConsoReviewColumnType._

import java.time.OffsetDateTime

class SignalConsoReviewTable(tag: Tag)
    extends TypedDatabaseTable[SignalConsoReview, SignalConsoReviewId](tag, "signalconso_review") {

  def evaluation = column[SignalConsoEvaluation]("evaluation")
  def details = column[Option[String]]("details")
  def creationDate = column[OffsetDateTime]("creation_date")
  def platform = column[Os]("platform")

  type SignalConsoReviewData = (
      SignalConsoReviewId,
      SignalConsoEvaluation,
      Option[String],
      OffsetDateTime,
      Os
  )

  def constructSignalConsoReview: SignalConsoReviewData => SignalConsoReview = {
    case (id, evaluation, details, creationDate, platform) =>
      SignalConsoReview(id, evaluation, details, creationDate, platform)
  }

  def extractSignalConsoReview: PartialFunction[SignalConsoReview, SignalConsoReviewData] = {
    case SignalConsoReview(id, evaluation, creationDate, details, platform) =>
      (id, evaluation, creationDate, details, platform)
  }

  def * =
    (
      id,
      evaluation,
      details,
      creationDate,
      platform
    ) <> (constructSignalConsoReview, extractSignalConsoReview.lift)
}

object SignalConsoReviewTable {
  val table = TableQuery[SignalConsoReviewTable]
}
