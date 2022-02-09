package models.report

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress
import utils.SIRET
import utils.URL
import Tag.ReportTag._

import java.time.OffsetDateTime
import java.util.UUID

case class ReportToExternal(
    id: UUID,
    creationDate: OffsetDateTime,
    category: String,
    subcategories: List[String],
    details: List[DetailInputValue],
    postalCode: Option[String],
    siret: Option[SIRET],
    websiteURL: Option[URL],
    phone: Option[String],
    firstName: String,
    lastName: String,
    email: EmailAddress,
    contactAgreement: Boolean,
    description: Option[String],
    effectiveDate: Option[String],
    reponseconsoCode: List[String],
    ccrfCode: List[String],
    tags: List[String]
) {}

object ReportToExternal {
  def fromReport(r: Report) =
    ReportToExternal(
      id = r.id,
      creationDate = r.creationDate,
      category = r.category,
      subcategories = r.subcategories,
      details = r.details,
      siret = r.companySiret,
      postalCode = r.companyAddress.postalCode,
      websiteURL = r.websiteURL.websiteURL,
      phone = r.phone,
      firstName = r.firstName,
      lastName = r.lastName,
      email = r.email,
      contactAgreement = r.contactAgreement,
      description = r.details
        .filter(d => d.label.matches("Quel est le problème.*"))
        .map(_.value)
        .headOption,
      effectiveDate = r.details
        .filter(d => d.label.matches("Date .* (constat|contrat|rendez-vous|course) .*"))
        .map(_.value)
        .headOption,
      reponseconsoCode = r.reponseconsoCode,
      ccrfCode = r.ccrfCode,
      tags = r.tags.map(_.translate())
    )

  implicit val format: OFormat[ReportToExternal] = Json.format[ReportToExternal]
}

case class ReportFileToExternal(
    id: UUID,
    filename: String
)

object ReportFileToExternal {

  def fromReportFile(reportFile: ReportFile) = ReportFileToExternal(
    id = reportFile.id,
    filename = reportFile.filename
  )

  implicit val format: OFormat[ReportFileToExternal] = Json.format[ReportFileToExternal]
}

case class ReportWithFilesToExternal(
    report: ReportToExternal,
    files: List[ReportFileToExternal]
)

object ReportWithFilesToExternal {
  implicit val format: OFormat[ReportWithFilesToExternal] = Json.format[ReportWithFilesToExternal]

  def fromReportWithFiles(reportWithFiles: ReportWithFiles) = ReportWithFilesToExternal(
    report = ReportToExternal.fromReport(reportWithFiles.report),
    files = reportWithFiles.files.map(ReportFileToExternal.fromReportFile)
  )
}
