package models.report

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.SIRET

import java.time.OffsetDateTime

/** Temporary to explore some data with external tableau team, will be removed as soon as finished
  */
case class TableauReport(
    creationDate: OffsetDateTime,
    ccrfCode: List[String],
    companyName: Option[String],
    companyPostalCode: Option[String],
    companySiret: Option[SIRET],
    details: List[DetailInputValue],
    websiteURL: Option[String],
    activityCode: Option[String]
)

object TableauReport {
  implicit val ReportTableauFormat: OFormat[TableauReport] = Json.format[TableauReport]
}
