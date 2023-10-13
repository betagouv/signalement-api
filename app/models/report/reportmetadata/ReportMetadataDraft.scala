package models.report.reportmetadata

import ai.x.play.json.Encoders.encoder
import ai.x.play.json.Jsonx

import java.util.UUID
import scala.annotation.nowarn
import play.api.libs.json.OFormat

case class ReportMetadataDraft(
    isMobileApp: Boolean,
    os: Option[Os]
) {
  def toReportMetadata(reportId: UUID) =
    ReportMetadata(
      reportId = reportId,
      isMobileApp = isMobileApp,
      os = os
    )
}

object ReportMetadataDraft {

  @nowarn
  implicit val jsonFormat: OFormat[ReportMetadataDraft] = Jsonx.formatCaseClass[ReportMetadataDraft]

}
