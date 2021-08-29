package models

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import utils.EnumUtils
import java.util.UUID

case class ReportResponse(
    responseType: ReportResponseType.Value,
    consumerDetails: String,
    dgccrfDetails: Option[String],
    fileIds: List[UUID]
)

object ReportResponse {
  implicit val reportResponse: OFormat[ReportResponse] = Json.format[ReportResponse]
}

object ReportResponseType extends Enumeration {
  val ACCEPTED, REJECTED, NOT_CONCERNED = Value

  implicit val enumReads: Reads[ReportResponseType.Value] = EnumUtils.enumReads(ReportResponseType)
  implicit def enumWrites: Writes[ReportResponseType.Value] = EnumUtils.enumWrites
}

case class ReviewOnReportResponse(
    positive: Boolean,
    details: Option[String]
)

object ReviewOnReportResponse {
  implicit val reviewOnReportResponse: OFormat[ReviewOnReportResponse] = Json.format[ReviewOnReportResponse]
}
