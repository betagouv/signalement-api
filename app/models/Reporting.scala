package models

import java.time.LocalDate
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Reporting(
                      id: UUID,
                      companyType: String,
                      anomalyCategory: String,
                      anomalyPrecision: Option[String],
                      companyName: String,
                      companyAddress: String,
                      companySiret: Option[String],
                      anomalyDate: LocalDate,
                      anomalyTimeSlot: Option[Int],
                      description: Option[String],
                      firstName: String,
                      lastName: String,
                      email: String,
                      contactAgreement: Boolean,
                      ticketFileId: Option[Long],
                      anomalyFileId: Option[Long]
                       )
object Reporting {

  implicit val reportingFormat: OFormat[Reporting] = Json.format[Reporting]

}