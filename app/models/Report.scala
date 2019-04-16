package models

import java.time.LocalDateTime
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
                   firstName: String,
                   lastName: String,
                   email: String,
                   contactAgreement: Boolean,
                   files: List[File]
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

  implicit def string2detailInputValue(input: String): DetailInputValue = {
    input match {
      case input if input.contains(':') => DetailInputValue(input.substring(0, input.indexOf(':') + 1), input.substring(input.indexOf(':') + 1).trim)
      case input => DetailInputValue("Précision :", input)
    }
  }
}