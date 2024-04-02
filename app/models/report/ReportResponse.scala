package models.report

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.report.reportfile.ReportFileId
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ReportResponse(
    responseType: ReportResponseType,
    consumerDetails: String,
    dgccrfDetails: Option[String],
    fileIds: List[ReportFileId],
    responseDetails: ResponseDetails,
    otherResponseDetails: Option[String]
)

object ReportResponse {
  implicit val reportResponse: OFormat[ReportResponse] = Json.format[ReportResponse]
}

sealed trait ReportResponseType extends EnumEntry

object ReportResponseType extends PlayEnum[ReportResponseType] {

  final case object ACCEPTED      extends ReportResponseType
  final case object REJECTED      extends ReportResponseType
  final case object NOT_CONCERNED extends ReportResponseType

  override def values: IndexedSeq[ReportResponseType] = findValues

  def translate(responseType: ReportResponseType): String =
    responseType match {
      case ACCEPTED      => "Pris en compte"
      case REJECTED      => "Infondé"
      case NOT_CONCERNED => "Mal attribué"
    }
}

sealed trait ResponseDetails extends EnumEntry

object ResponseDetails extends PlayEnum[ResponseDetails] {

  override def values: IndexedSeq[ResponseDetails] = findValues

  final case object REFUND             extends ResponseDetails
  final case object REPLACEMENT        extends ResponseDetails
  final case object DELIVERY           extends ResponseDetails
  final case object DIRECTIONS_FOR_USE extends ResponseDetails
  final case object CONFORM            extends ResponseDetails
  final case object ADAPT_PRACTICES    extends ResponseDetails

  final case object LAWFUL               extends ResponseDetails
  final case object DID_NOT_HAPPEN       extends ResponseDetails
  final case object WRONG_INTERPRETATION extends ResponseDetails

  final case object PARTNERSHIP        extends ResponseDetails
  final case object SAME_GROUP_COMPANY extends ResponseDetails
  final case object HOMONYM            extends ResponseDetails
  final case object UNKNOWN_COMPANY    extends ResponseDetails
  final case object IDENTITY_FRAUD     extends ResponseDetails

  final case object OTHER extends ResponseDetails
}
