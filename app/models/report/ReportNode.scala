package models.report

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites

case class ReportNode(
    name: String,
    label: String,
    overriddenCategory: Option[String],
    var count: Int,
    var reclamations: Int,
    var children: List[ReportNode],
    tags: List[String],
    isBlocking: Boolean,
    id: Option[String]
)

object ReportNode {
  implicit val format: OFormat[ReportNode] = Json.format[ReportNode]
}

case class ReportNodes(fr: List[ReportNode], en: List[ReportNode])

object ReportNodes {
  implicit val writes: OWrites[ReportNodes] = Json.writes[ReportNodes]
}
