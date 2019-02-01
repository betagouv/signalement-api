package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Report(
                   id: UUID,
                   category: String,
                   subcategory: Option[String],
                   precision: Option[String],
                   companyName: String,
                   companyAddress: String,
                   companyPostalCode: Option[String],
                   companySiret: Option[String],
                   creationDate: LocalDate,
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
object Report {

  implicit val reportingFormat: OFormat[Report] = Json.format[Report]

}