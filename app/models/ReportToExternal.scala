package models

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.EmailAddress
import utils.SIRET
import utils.URL

import java.util.UUID

case class ReportToExternal(
    id: UUID,
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
    effectiveDate: Option[String]
) {}

object ReportToExternal {
  def fromReport(report: Report) =
    ReportToExternal(
      id = report.id,
      category = report.category,
      subcategories = report.subcategories,
      details = report.details,
      siret = report.companySiret,
      postalCode = report.companyAddress.postalCode,
      websiteURL = report.websiteURL.websiteURL,
      phone = report.phone,
      firstName = report.firstName,
      lastName = report.lastName,
      email = report.email,
      contactAgreement = report.contactAgreement,
      description = report.details
        .filter(d => d.label.matches("Quel est le problème.*"))
        .map(_.value)
        .headOption,
      effectiveDate = report.details
        .filter(d => d.label.matches("Date .* (constat|contrat|rendez-vous|course) .*"))
        .map(_.value)
        .headOption
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
