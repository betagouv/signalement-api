package models.report.review

import models.UserRole
import models.UserRole.Professionnel
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
    userRole match {
      case Professionnel =>
        (r: ConsumerReviewApi) =>
          Json.obj(
            "evaluation" -> r.evaluation
          )
      case _ => Json.writes[ConsumerReviewApi]
    }

}
