package models.report

import models.Address
import play.api.libs.json.Json
import utils.EmailAddress
import utils.SIRET
import utils.URL

import java.util.UUID

case class ReportDraft(
    category: String,
    subcategories: List[String],
    details: List[DetailInputValue],
    companyName: Option[String],
    companyAddress: Option[Address],
    companySiret: Option[SIRET],
    companyActivityCode: Option[String],
    websiteURL: Option[URL],
    phone: Option[String],
    firstName: String,
    lastName: String,
    email: EmailAddress,
    contactAgreement: Boolean,
    employeeConsumer: Boolean,
    forwardToReponseConso: Option[Boolean] = Some(false),
    fileIds: List[UUID],
    vendor: Option[String] = None,
    tags: List[String] = Nil,
    reponseconsoCode: Option[List[String]] = None,
    ccrfCode: Option[List[String]] = None
) {

  def generateReport: Report = {
    val report = Report(
      category = category,
      subcategories = subcategories,
      details = details,
      companyId = None,
      companyName = companyName,
      companyAddress = companyAddress.getOrElse(Address()),
      companySiret = companySiret,
      websiteURL = WebsiteURL(websiteURL, websiteURL.flatMap(_.getHost)),
      phone = phone,
      firstName = firstName,
      lastName = lastName,
      email = email,
      contactAgreement = contactAgreement,
      employeeConsumer = employeeConsumer,
      status = ReportStatus.NA,
      forwardToReponseConso = forwardToReponseConso.getOrElse(false),
      vendor = vendor,
      tags = tags
        .map(ReportTag.fromDisplayOrEntryName(_))
        .distinct
        .filterNot(tag => tag == ReportTag.LitigeContractuel && employeeConsumer),
      reponseconsoCode = reponseconsoCode.getOrElse(Nil),
      ccrfCode = ccrfCode.getOrElse(Nil)
    )
    report.copy(status = report.initialStatus())
  }
}

object ReportDraft {
  def isValid(draft: ReportDraft): Boolean =
    (draft.companySiret.isDefined
      || draft.websiteURL.isDefined
      || draft.tags.map(ReportTag.fromDisplayOrEntryName(_)).contains(ReportTag.Influenceur) && draft.companyAddress
        .exists(_.postalCode.isDefined)
      || (draft.companyAddress.exists(x => x.country.isDefined || x.postalCode.isDefined))
      || draft.phone.isDefined)

  implicit val draftReportReads = Json.reads[ReportDraft]
  implicit val draftReportWrites = Json.writes[ReportDraft]
}
