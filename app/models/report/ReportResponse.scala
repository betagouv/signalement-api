package models.report

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.util.UUID

case class ReportResponse(
    responseType: ReportResponseType,
    consumerDetails: String,
    dgccrfDetails: Option[String],
    fileIds: List[UUID]
)

object ReportResponse {
  implicit val reportResponse: OFormat[ReportResponse] = Json.format[ReportResponse]
}

sealed trait ReportResponseType extends EnumEntry

object ReportResponseType extends PlayEnum[ReportResponseType] {

  final case object ACCEPTED extends ReportResponseType
  final case object REJECTED extends ReportResponseType
  final case object NOT_CONCERNED extends ReportResponseType

  override def values: IndexedSeq[ReportResponseType] = findValues

}

case class ReviewOnReportResponse(
    positive: Boolean,
    details: Option[String]
)

object ReviewOnReportResponse {
  implicit val reviewOnReportResponse: OFormat[ReviewOnReportResponse] = Json.format[ReviewOnReportResponse]
}
