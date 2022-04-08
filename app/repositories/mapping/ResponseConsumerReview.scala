package repositories.mapping

import models.report.review.ResponseConsumerReviewId
import models.report.review.ResponseEvaluation
import play.api.Logger
import repositories.PostgresProfile.api._

import java.util.UUID

object ResponseConsumerReview {

  val logger: Logger = Logger(this.getClass)

  implicit val ResponseEvaluationColumnType =
    MappedColumnType.base[ResponseEvaluation, String](
      _.entryName,
      ResponseEvaluation.namesToValuesMap
    )

  implicit val ResponseConsumerReviewIdColumnType =
    MappedColumnType.base[ResponseConsumerReviewId, UUID](
      _.value,
      ResponseConsumerReviewId(_)
    )

}
