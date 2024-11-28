package repositories.albert

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.util.UUID

case class AlbertClassification(
    reportId: UUID,
    category: Option[String],
    confidenceScore: Option[Double],
    explanation: Option[String],
    summary: Option[String],
    raw: String,
    codeConso: Option[String]
)

object AlbertClassification {
  implicit val format: OFormat[AlbertClassification] = Json.format[AlbertClassification]

  def fromAlbertApi(reportId: UUID, json: JsValue, codeConso: Option[String]): AlbertClassification =
    AlbertClassification(
      reportId = reportId,
      category = (json \ "category").asOpt[String],
      confidenceScore = (json \ "confidence_score").asOpt[Double],
      explanation = (json \ "explanation").asOpt[String],
      summary = (json \ "summary").asOpt[String],
      raw = Json.stringify(json),
      codeConso = codeConso
    )
}
