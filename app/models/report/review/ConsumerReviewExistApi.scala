package models.report.review

import play.api.libs.json.Json
import play.api.libs.json.OWrites

case class ConsumerReviewExistApi(value: Boolean) extends AnyVal

object ConsumerReviewExistApi {
  implicit val consumerReviewExistApi: OWrites[ConsumerReviewExistApi] =
    (consumerReviewExistApi: ConsumerReviewExistApi) => Json.obj("value" -> consumerReviewExistApi.value)
}
