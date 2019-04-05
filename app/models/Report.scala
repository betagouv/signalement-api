package models

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import com.github.tminglei.slickpg.composite.Struct
import play.api.libs.json.{Json, OFormat}

case class Report(
                   id: Option[UUID],
                   category: String,
                   subcategories: List[String],
                   details: List[DetailInputValue],
                   companyName: String,
                   companyAddress: String,
                   companyPostalCode: Option[String],
                   companySiret: Option[String],
                   creationDate: Option[LocalDateTime],
                   anomalyDate: Option[LocalDate],
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


case class  DetailInputValue (
                           label: String,
                           value: String
                 ) extends Struct

object DetailInputValue {
  implicit val detailInputValueFormat: OFormat[DetailInputValue] = Json.format[DetailInputValue]

}