package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Report(
                   id: Option[UUID],
                   category: String,
                   subcategory: Option[String],
                   precision: Option[String],
                   companyName: String,
                   companyAddress: String,
                   companyPostalCode: Option[String],
                   companySiret: Option[String],
                   creationDate: Option[LocalDateTime],
                   anomalyDate: LocalDate,
                   anomalyTimeSlot: Option[Int],
                   description: Option[String],
                   firstName: String,
                   lastName: String,
                   email: String,
                   contactAgreement: Boolean,
                   fileIds: List[UUID]
                 )
object Report {
  implicit val reportFormat: OFormat[Report] = Json.format[Report]

}