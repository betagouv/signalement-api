package models.report

import ai.x.play.json.Encoders.encoder
import ai.x.play.json.Jsonx
import com.github.tminglei.slickpg.composite.Struct
import models.UserRole
import models.company.Address
import models.report.ReportTag.jsonFormat
import models.report.reportfile.ReportFileId
import play.api.libs.json._
import utils.Constants.ActionEvent.ActionEventValue
import utils.Country
import utils.EmailAddress
import utils.SIRET

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.annotation.nowarn

case class Report(
    id: UUID = UUID.randomUUID(),
    gender: Option[Gender],
    category: String,
    subcategories: List[String],
    details: List[DetailInputValue],
    influencer: Option[Influencer],
    companyId: Option[UUID],
    companyName: Option[String],
    companyCommercialName: Option[String],
    companyEstablishmentCommercialName: Option[String],
    companyBrand: Option[String],
    companyAddress: Address,
    companySiret: Option[SIRET],
    companyActivityCode: Option[String],
    websiteURL: WebsiteURL,
    phone: Option[String],
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    firstName: String,
    lastName: String,
    email: EmailAddress,
    consumerPhone: Option[String] = None,
    consumerReferenceNumber: Option[String] = None,
    contactAgreement: Boolean,
    employeeConsumer: Boolean,
    forwardToReponseConso: Boolean = false,
    status: ReportStatus = ReportStatus.NA,
    vendor: Option[String] = None,
    tags: List[ReportTag] = Nil,
    reponseconsoCode: List[String] = Nil,
    ccrfCode: List[String] = Nil,
    expirationDate: OffsetDateTime,
    visibleToPro: Boolean,
    lang: Option[Locale],
    reopenDate: Option[OffsetDateTime] = None,
    barcodeProductId: Option[UUID],
    train: Option[Train],
    station: Option[String],
    rappelConsoId: Option[Int]
) {

  def shortURL() = websiteURL.websiteURL.map(_.value.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)", ""))

  def isContractualDispute() = tags.contains(ReportTag.LitigeContractuel)

  def needWorkflowAttachment() = visibleToPro &&
    !isContractualDispute()

  def isReadByPro = ReportStatus.statusReadByPro.contains(status)

  def getDescription: Option[String] =
    // /!\ Some reports don't have description
    // in some subcategories, there is no description field (or it's called differently)
    details.find(_.label == "Description :").map(_.value.trim).filterNot(_.isEmpty)

  def getReponseConsoDescription: Option[String] =
    details
      .find(x => x.label == "Your question :" || x.label == "Votre question :")
      .map(_.value.trim)
      .filterNot(_.isEmpty)
}

object Report {

  def initialStatus(
      employeeConsumer: Boolean,
      visibleToPro: Boolean,
      companySiret: Option[SIRET],
      companyCountry: Option[Country]
  ): ReportStatus =
    if (employeeConsumer) ReportStatus.InformateurInterne
    else if (!visibleToPro) ReportStatus.NA
    else if (companySiret.isEmpty) ReportStatus.NA
    else if (companySiret.nonEmpty && companyCountry.isDefined)
      ReportStatus.NA // Company has a french SIRET but a foreign address, we can't send any letter to it
    else ReportStatus.TraitementEnCours

  @nowarn
  private[this] val jsonFormatX           = Jsonx.formatCaseClass[Report]
  implicit val reportReads: Reads[Report] = jsonFormatX

  implicit def writer(implicit userRole: Option[UserRole]): Writes[Report] = (report: Report) =>
    Json.obj(
      "id"                                 -> report.id,
      "category"                           -> report.category,
      "subcategories"                      -> report.subcategories,
      "details"                            -> report.details,
      "influencer"                         -> report.influencer,
      "companyId"                          -> report.companyId,
      "companyName"                        -> report.companyName,
      "companyCommercialName"              -> report.companyCommercialName,
      "companyEstablishmentCommercialName" -> report.companyEstablishmentCommercialName,
      "companyBrand"                       -> report.companyBrand,
      "companyAddress"                     -> Json.toJson(report.companyAddress),
      "companySiret"                       -> report.companySiret,
      "creationDate"                       -> report.creationDate,
      "contactAgreement"                   -> report.contactAgreement,
      "status"                             -> report.status,
      "websiteURL"                         -> report.websiteURL.websiteURL,
      "host"                               -> report.websiteURL.host,
      "vendor"                             -> report.vendor,
      "tags"                               -> report.tags,
      "activityCode"                       -> report.companyActivityCode,
      "expirationDate"                     -> report.expirationDate,
      "train"                              -> report.train,
      "station"                            -> report.station,
      "barcodeProductId"                   -> report.barcodeProductId,
      "rappelConsoId"                      -> report.rappelConsoId
    ) ++ ((userRole, report.contactAgreement) match {
      case (Some(UserRole.Professionnel), false) => Json.obj()
      case (_, _) =>
        Json.obj(
          "firstName"               -> report.firstName,
          "lastName"                -> report.lastName,
          "email"                   -> report.email,
          "consumerReferenceNumber" -> report.consumerReferenceNumber
        )
    }) ++ (userRole match {
      case Some(UserRole.Professionnel) => Json.obj()
      case _ =>
        Json.obj(
          "ccrfCode"         -> report.ccrfCode,
          "phone"            -> report.phone,
          "consumerPhone"    -> report.consumerPhone,
          "employeeConsumer" -> report.employeeConsumer,
          "reponseconsoCode" -> report.reponseconsoCode,
          "gender"           -> report.gender,
          "visibleToPro"     -> report.visibleToPro
        )
    })
}

case class DetailInputValue(
    label: String,
    value: String
) extends Struct

object DetailInputValue {
  implicit val detailInputValueFormat: OFormat[DetailInputValue] = Json.format[DetailInputValue]

  val DefaultKey = "Précision :"
  def toDetailInputValue(input: String): DetailInputValue =
    input match {
      case input if input.contains(':') =>
        DetailInputValue(input.substring(0, input.indexOf(':') + 1), input.substring(input.indexOf(':') + 1).trim)
      case input =>
        DetailInputValue(DefaultKey, input)
    }

  def detailInputValuetoString(detailInputValue: DetailInputValue): String = detailInputValue.label match {
    case DefaultKey => detailInputValue.value
    case _          => s"${detailInputValue.label} ${detailInputValue.value}"
  }

}

case class ReportConsumerUpdate(
    firstName: String,
    lastName: String,
    email: EmailAddress,
    consumerReferenceNumber: Option[String]
)

object ReportConsumerUpdate {
  implicit val format: OFormat[ReportConsumerUpdate] = Json.format[ReportConsumerUpdate]
}

case class ReportAction(
    actionType: ActionEventValue,
    details: Option[String],
    fileIds: List[ReportFileId]
)

object ReportAction {
  implicit val reportAction: OFormat[ReportAction] = Json.format[ReportAction]
}
