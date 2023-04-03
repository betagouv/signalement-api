package models.report.review

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ConsumerReviewExistApi(
    value: Boolean
)

object ConsumerReviewExistApi {
  implicit val consumerReviewExistApi: OFormat[ConsumerReviewExistApi] = Json.format[ConsumerReviewExistApi]
}
