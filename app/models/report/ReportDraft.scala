package models.report

import ai.x.play.json.Encoders.encoder
import ai.x.play.json.Jsonx
import models.company.Address
import models.company.Company
import models.report.ReportTag.ReportTagHiddenToProfessionnel
import models.report.ReportTag.TranslationReportTagReads
import models.report.reportfile.ReportFileId
import models.report.reportmetadata.ReportMetadataDraft
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import utils.EmailAddress
import utils.SIRET
import utils.URL

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.annotation.nowarn

case class ReportDraft(
    gender: Option[Gender],
    category: String,
    subcategories: List[String],
    details: List[DetailInputValue],
    influencer: Option[Influencer],
    companyName: Option[String],
    companyBrand: Option[String],
    companyAddress: Option[Address],
    companySiret: Option[SIRET],
    companyActivityCode: Option[String],
    companyIsHeadOffice: Option[Boolean],
    companyIsOpen: Option[Boolean],
    companyIsPublic: Option[Boolean],
    websiteURL: Option[URL],
    phone: Option[String],
    firstName: String,
    lastName: String,
    email: EmailAddress,
    consumerPhone: Option[String],
    consumerReferenceNumber: Option[String],
    contactAgreement: Boolean,
    employeeConsumer: Boolean,
    forwardToReponseConso: Option[Boolean] = Some(false),
    fileIds: List[ReportFileId],
    vendor: Option[String] = None,
    tags: List[ReportTag] = Nil,
    reponseconsoCode: Option[List[String]] = None,
    ccrfCode: Option[List[String]] = None,
    lang: Option[Locale] = None,
    metadata: Option[ReportMetadataDraft] = None
) {

  def generateReport(
      maybeCompanyId: Option[UUID],
      socialNetworkCompany: Option[Company],
      creationDate: OffsetDateTime,
      expirationDate: OffsetDateTime,
      reportId: UUID = UUID.randomUUID()
  ): Report =
    Report(
      reportId,
      gender = gender,
      creationDate = creationDate,
      category = category,
      subcategories = subcategories,
      details = details,
      influencer = influencer,
      companyId = maybeCompanyId.orElse(socialNetworkCompany.map(_.id)),
      companyName = companyName.orElse(socialNetworkCompany.map(_.name)),
      companyBrand = companyBrand.orElse(socialNetworkCompany.flatMap(_.brand)),
      companyAddress = companyAddress.orElse(socialNetworkCompany.map(_.address)).getOrElse(Address()),
      companySiret = companySiret.orElse(socialNetworkCompany.map(_.siret)),
      companyActivityCode = companyActivityCode.orElse(socialNetworkCompany.flatMap(_.activityCode)),
      websiteURL = WebsiteURL(websiteURL, websiteURL.flatMap(_.getHost)),
      phone = phone,
      firstName = firstName,
      lastName = lastName,
      email = email,
      consumerPhone = consumerPhone,
      consumerReferenceNumber = consumerReferenceNumber,
      contactAgreement = contactAgreement,
      employeeConsumer = employeeConsumer,
      status = Report.initialStatus(
        employeeConsumer = employeeConsumer,
        visibleToPro = shouldBeVisibleToPro(),
        companySiret = companySiret.orElse(socialNetworkCompany.map(_.siret)),
        companyCountry = companyAddress.orElse(socialNetworkCompany.map(_.address)).flatMap(_.country)
      ),
      forwardToReponseConso = forwardToReponseConso.getOrElse(false),
      vendor = vendor,
      tags = tags.distinct
        .filterNot(tag => tag == ReportTag.LitigeContractuel && employeeConsumer),
      reponseconsoCode = reponseconsoCode.getOrElse(Nil),
      ccrfCode = ccrfCode.getOrElse(Nil),
      expirationDate = expirationDate,
      visibleToPro = shouldBeVisibleToPro(),
      lang = lang
    )

  def shouldBeVisibleToPro(): Boolean =
    !employeeConsumer && tags
      .intersect(ReportTagHiddenToProfessionnel)
      .isEmpty
}

object ReportDraft {
  def isValid(draft: ReportDraft): Boolean =
    (draft.companySiret.isDefined
      || draft.websiteURL.isDefined
      || draft.tags.contains(ReportTag.Influenceur) && draft.companyAddress
        .exists(_.postalCode.isDefined)
      || (draft.companyAddress.exists(x => x.country.isDefined || x.postalCode.isDefined))
      || draft.phone.isDefined
      || draft.influencer.isDefined)

  /** Used as workaround to parse values from their translation as signalement-app is pushing transaction instead of
    * entry name Make sure no translated values is passed as ReportTag to remove this reads
    */
  implicit val reportTagReads: Reads[ReportTag] = TranslationReportTagReads
  @nowarn
  implicit val draftReportReads: OFormat[ReportDraft] = Jsonx.formatCaseClass[ReportDraft]

}
