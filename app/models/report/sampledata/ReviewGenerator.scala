package models.report.sampledata

import models.report.review.ConsumerReviewApi
import models.report.review.ResponseEvaluation.Negative
import models.report.review.ResponseEvaluation.Neutral

object ReviewGenerator {

  def randomConsumerReview() =
    ConsumerReviewApi(
      evaluation = Negative,
      details = Some(
        "Je ne veux pas d’un avoir. Je veux la livraison du produit qui a été proposé en promotion et qui est toujours disponible en stock . Cordialement "
      )
    )

  def randomEngagementReview() =
    ConsumerReviewApi(
      evaluation = Neutral,
      details = Some(
        "Je n’ai jamais été recontacté par un superviseur"
      )
    )

}
