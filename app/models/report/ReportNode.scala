package models.report

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ReportNode(name: String, var count: Int, var children: List[ReportNode], tags: List[String], id: String)

object ReportNode {
  implicit val format: OFormat[ReportNode] = Json.format[ReportNode]
}
