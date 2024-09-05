package models.report.review

import models.UserRole
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

case class ConsumerReviewApi(
    evaluation: ResponseEvaluation,
    details: Option[String]
)

object ConsumerReviewApi {
  implicit val consumerReviewApiReads: Reads[ConsumerReviewApi] = Json.reads[ConsumerReviewApi]
  def consumerReviewApiWrites(userRole: UserRole): Writes[ConsumerReviewApi] =
    (r: ConsumerReviewApi) =>
      Json.obj(
        "evaluation" -> r.evaluation
      ) ++ (if (UserRole.isAdminOrAgent(userRole))
              Json.obj(
                "details" -> r.details
              )
            else Json.obj())

}
