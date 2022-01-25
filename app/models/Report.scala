package models

import com.github.tminglei.slickpg.composite.Struct
import play.api.libs.json._
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.Tags
import utils.EmailAddress
import utils.SIRET
import utils.URL

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteURL(websiteURL: Option[URL], host: Option[String])

object WebsiteURL {
  implicit val WebsiteURLFormat: OFormat[WebsiteURL] = Json.format[WebsiteURL]
}

case class DraftReport(
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
      tags = tags.distinct.filterNot(tag => tag == Tags.ContractualDispute && employeeConsumer),
      reponseconsoCode = reponseconsoCode.getOrElse(Nil),
      ccrfCode = ccrfCode.getOrElse(Nil)
    )
    report.copy(status = report.initialStatus())
  }
}

object DraftReport {
  def isValid(draft: DraftReport): Boolean =
    (draft.companySiret.isDefined
      || draft.websiteURL.isDefined
      || draft.tags.contains(Tags.Influenceur) && draft.companyAddress.exists(_.postalCode.isDefined)
      || (draft.companyAddress.exists(x => x.country.isDefined || x.postalCode.isDefined))
      || draft.phone.isDefined)

  implicit val draftReportReads = Json.reads[DraftReport]
  implicit val draftReportWrites = Json.writes[DraftReport]
}

case class Report(
    id: UUID = UUID.randomUUID(),
    category: String,
    subcategories: List[String],
    details: List[DetailInputValue],
    companyId: Option[UUID],
    companyName: Option[String],
    companyAddress: Address,
    companySiret: Option[SIRET],
    websiteURL: WebsiteURL,
    phone: Option[String],
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    firstName: String,
    lastName: String,
    email: EmailAddress,
    contactAgreement: Boolean,
    employeeConsumer: Boolean,
    forwardToReponseConso: Boolean = false,
    status: ReportStatus = ReportStatus.NA,
    vendor: Option[String] = None,
    tags: List[String] = Nil,
    reponseconsoCode: List[String] = Nil,
    ccrfCode: List[String] = Nil
) {

  def initialStatus() =
    if (employeeConsumer) ReportStatus.LanceurAlerte
    else if (
      companySiret.isDefined && tags.intersect(Seq(Tags.ReponseConso, Tags.DangerousProduct, Tags.Bloctel)).isEmpty
    )
      ReportStatus.TraitementEnCours
    else ReportStatus.NA

  def shortURL() = websiteURL.websiteURL.map(_.value.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)", ""))

  def isContractualDispute() = tags.contains(Tags.ContractualDispute)

  def needWorkflowAttachment() = !employeeConsumer &&
    !isContractualDispute() &&
    tags.intersect(Seq(Tags.DangerousProduct, Tags.ReponseConso)).isEmpty

  def isTransmittableToPro() = !employeeConsumer && !forwardToReponseConso
}

object Report {

  implicit val reportReader = Json.reads[Report]

  implicit def writer(implicit userRole: Option[UserRole] = None) = new Writes[Report] {
    def writes(report: Report) =
      Json.obj(
        "id" -> report.id,
        "category" -> report.category,
        "subcategories" -> report.subcategories,
        "details" -> report.details,
        "companyId" -> report.companyId,
        "companyName" -> report.companyName,
        "companyAddress" -> Json.toJson(report.companyAddress),
        "companySiret" -> report.companySiret,
        "creationDate" -> report.creationDate,
        "contactAgreement" -> report.contactAgreement,
        "employeeConsumer" -> report.employeeConsumer,
        "status" -> report.status,
        "websiteURL" -> report.websiteURL.websiteURL,
        "host" -> report.websiteURL.host,
        "phone" -> report.phone,
        "vendor" -> report.vendor,
        "tags" -> report.tags,
        "reponseconsoCode" -> report.reponseconsoCode,
        "ccrfCode" -> report.ccrfCode
      ) ++ ((userRole, report.contactAgreement) match {
        case (Some(UserRole.Professionnel), false) => Json.obj()
        case (_, _) =>
          Json.obj(
            "firstName" -> report.firstName,
            "lastName" -> report.lastName,
            "email" -> report.email
          )
      })
  }
}

case class ReportWithFiles(
    report: Report,
    files: List[ReportFile]
)

object ReportWithFiles {
  implicit def writer(implicit userRole: Option[UserRole] = None) = Json.writes[ReportWithFiles]
}

case class DetailInputValue(
    label: String,
    value: String
) extends Struct

object DetailInputValue {
  implicit val detailInputValueFormat: OFormat[DetailInputValue] = Json.format[DetailInputValue]

  def toDetailInputValue(input: String): DetailInputValue =
    input match {
      case input if input.contains(':') =>
        DetailInputValue(input.substring(0, input.indexOf(':') + 1), input.substring(input.indexOf(':') + 1).trim)
      case input => DetailInputValue("Précision :", input)
    }
}

/** @deprecated Keep it for compat purpose but no longer used in new dashboard */
case class DeprecatedCompanyWithNbReports(company: Company, count: Int)

/** @deprecated Keep it for compat purpose but no longer used in new dashboard */
object DeprecatedCompanyWithNbReports {

  implicit val companyWithNbReportsWrites = new Writes[DeprecatedCompanyWithNbReports] {
    def writes(data: DeprecatedCompanyWithNbReports) = Json.obj(
      "companySiret" -> data.company.siret,
      "companyName" -> data.company.name,
      "companyAddress" -> Json.toJson(data.company.address),
      "count" -> data.count
    )
  }

  implicit val paginatedCompanyWithNbReportsWriter = Json.writes[PaginatedResult[DeprecatedCompanyWithNbReports]]
}

case class ReportCompany(
    name: String,
    address: Address,
    siret: SIRET,
    activityCode: Option[String]
)

object ReportCompany {
  implicit val format = Json.format[ReportCompany]
}

case class ReportConsumer(
    firstName: String,
    lastName: String,
    email: EmailAddress,
    contactAgreement: Boolean
)

object ReportConsumer {
  implicit val format = Json.format[ReportConsumer]
}

case class ReportAction(
    actionType: ActionEventValue,
    details: Option[String],
    fileIds: List[UUID]
)

object ReportAction {
  implicit val reportAction: OFormat[ReportAction] = Json.format[ReportAction]
}

sealed case class ReportCategory(value: String)

object ReportCategory {
  val Covid = ReportCategory("COVID-19 (coronavirus)")
  val CafeRestaurant = ReportCategory("Café / Restaurant")
  val AchatMagasin = ReportCategory("Achat / Magasin")
  val Service = ReportCategory("Services aux particuliers")
  val TelEauGazElec = ReportCategory("Téléphonie / Eau-Gaz-Electricité")
  val EauGazElec = ReportCategory("Eau / Gaz / Electricité")
  val TelFaiMedias = ReportCategory("Téléphonie / Fournisseur d'accès internet / médias")
  val BanqueAssuranceMutuelle = ReportCategory("Banque / Assurance / Mutuelle")
  val ProduitsObjets = ReportCategory("Produits / Objets")
  val Internet = ReportCategory("Internet (hors achats)")
  val TravauxRenovations = ReportCategory("Travaux / Rénovation")
  val VoyageLoisirs = ReportCategory("Voyage / Loisirs")
  val Immobilier = ReportCategory("Immobilier")
  val Sante = ReportCategory("Secteur de la santé")
  val VoitureVehicule = ReportCategory("Voiture / Véhicule")
  val Animaux = ReportCategory("Animaux")
  val DemarchesAdministratives = ReportCategory("Démarches administratives")

  def fromValue(v: String) =
    List(
      Covid,
      CafeRestaurant,
      AchatMagasin,
      Service,
      TelEauGazElec,
      EauGazElec,
      TelFaiMedias,
      BanqueAssuranceMutuelle,
      ProduitsObjets,
      Internet,
      TravauxRenovations,
      VoyageLoisirs,
      Immobilier,
      Sante,
      VoitureVehicule,
      Animaux,
      DemarchesAdministratives
    ).find(_.value == v).head

  implicit val reads = new Reads[ReportCategory] {
    def reads(json: JsValue): JsResult[ReportCategory] = json.validate[String].map(fromValue(_))
  }
  implicit val writes = new Writes[ReportCategory] {
    def writes(kind: ReportCategory) = Json.toJson(kind.value)
  }
}
