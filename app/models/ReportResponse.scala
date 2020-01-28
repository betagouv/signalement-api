package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat, Reads, Writes}
import utils.EnumUtils

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

case class AdviceOnReportResponse(
                           positive: Boolean,
                           details: Option[String]
                         )

object AdviceOnReportResponse {
  implicit val adviceOnReportResponse: OFormat[AdviceOnReportResponse] = Json.format[AdviceOnReportResponse]
}
