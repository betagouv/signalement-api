package models.report

import io.scalaland.chimney.dsl.TransformationOps
import models.report.reportfile.ReportFileId
import play.api.libs.json._

import java.time.OffsetDateTime
import java.util.UUID

case class ReportFileApi(
    id: ReportFileId,
    reportId: Option[UUID],
    creationDate: OffsetDateTime,
    filename: String,
    storageFilename: String,
    origin: ReportFileOrigin,
    isScanned: Boolean = false
)
object ReportFileApi {
  implicit val fileFormat: OFormat[ReportFileApi] = Json.format[ReportFileApi]

  def build(reportFile: ReportFile) =
    reportFile.into[ReportFileApi].withFieldComputed(_.isScanned, _.avOutput.isDefined).transform

  def buildForFileOwner(reportFile: ReportFile) =
    reportFile.into[ReportFileApi].withFieldConst(_.isScanned, true).transform
}
