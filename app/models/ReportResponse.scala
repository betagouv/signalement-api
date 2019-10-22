package models

import play.api.libs.json.{Json, OFormat, Reads, Writes}
import utils.EnumUtils

case class ReportResponse(
                           responseType: ReportResponseType.Value,
                           consumerDetails: String,
                           dgccrfDetails: Option[String]
                         )

object ReportResponse {
  implicit val reportResponse: OFormat[ReportResponse] = Json.format[ReportResponse]
}

object ReportResponseType extends Enumeration {
  val ACCEPTED, REJECTED, NOT_CONCERNED = Value

  implicit val enumReads: Reads[ReportResponseType.Value] = EnumUtils.enumReads(ReportResponseType)
  implicit def enumWrites: Writes[ReportResponseType.Value] = EnumUtils.enumWrites
}

