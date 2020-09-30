package models

import java.time.OffsetDateTime
import java.util.UUID

import com.github.tminglei.slickpg.composite.Struct
import play.api.libs.json._
import play.api.data.validation.ValidationError
import utils.Constants.ActionEvent.ActionEventValue
import play.api.libs.json.{Json, OFormat, Writes}
import utils.Constants.ReportStatus._
import utils.{Address, EmailAddress, SIRET, URL}


case class DraftReport(
                        category: String,
                        subcategories: List[String],
                        details: List[DetailInputValue],
                        companyName: Option[String],
                        companyAddress: Option[Address],
                        companyPostalCode: Option[String],
                        companySiret: Option[SIRET],
                        websiteURL: Option[URL],
                        firstName: String,
                        lastName: String,
                        email: EmailAddress,
                        contactAgreement: Boolean,
                        employeeConsumer: Boolean,
                        fileIds: List[UUID],
                        consumerActionsId: Option[String],
                        tags: List[String] = Nil
                      ) {

  def generateReport: Report = {
    val report = Report(
      UUID.randomUUID(),
      category,
      subcategories,
      details,
      None,
      companyName,
      companyAddress,
      companyPostalCode,
      companySiret,
      None,
      websiteURL,
      OffsetDateTime.now(),
      firstName,
      lastName,
      email,
      contactAgreement,
      employeeConsumer,
      NA,
      consumerActionsId,
      tags
    )
    report.copy(status = report.initialStatus)
  }
}
object DraftReport {
  implicit val draftReportReads = Json.reads[DraftReport].filter(draft => draft.companySiret.isDefined || draft.websiteURL.isDefined)
  implicit val draftReportWrites = Json.writes[DraftReport]
}

case class Report(
                   id: UUID,
                   category: String,
                   subcategories: List[String],
                   details: List[DetailInputValue],
                   companyId: Option[UUID],
                   companyName: Option[String],
                   companyAddress: Option[Address],
                   companyPostalCode: Option[String],
                   companySiret: Option[SIRET],
                   websiteId: Option[UUID],
                   websiteURL: Option[URL],
                   creationDate: OffsetDateTime,
                   firstName: String,
                   lastName: String,
                   email: EmailAddress,
                   contactAgreement: Boolean,
                   employeeConsumer: Boolean,
                   status: ReportStatusValue,
                   consumerActionsId: Option[String],
                   tags: List[String] = Nil
                 ) {

  def initialStatus() = {
    if (employeeConsumer) EMPLOYEE_REPORT
    else if (companySiret.isDefined) TRAITEMENT_EN_COURS
    else NA
  }

  def shortURL() = websiteURL.map(_.value.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)",""))

  def needWorkflowAttachment() = !employeeConsumer && consumerActionsId.isEmpty
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
        "companyName" -> report.companyName,
        "companyAddress" -> report.companyAddress,
        "companyPostalCode" -> report.companyPostalCode,
        "companySiret" -> report.companySiret,
        "creationDate" -> report.creationDate,
        "contactAgreement" -> report.contactAgreement,
        "employeeConsumer" -> report.employeeConsumer,
        "status" -> report.status,
        "websiteURL" -> report.websiteURL,
        "tags" -> report.tags
      ) ++ ((userRole, report.contactAgreement) match {
        case (Some(UserRoles.Pro), false) => Json.obj()
        case (_, _) => Json.obj(
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

case class CompanyWithNbReports(company: Company, count: Int)

object CompanyWithNbReports {

  implicit val companyWithNbReportsWrites = new Writes[CompanyWithNbReports] {
    def writes(data: CompanyWithNbReports) = Json.obj(
      "companyPostalCode" -> data.company.postalCode,
      "companySiret" -> data.company.siret,
      "companyName" -> data.company.name,
      "companyAddress" -> data.company.address,
      "count" -> data.count
    )
  }
}

case class ReportCompany(
                          name: String,
                          address: Address,
                          postalCode: String,
                          siret: SIRET
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
  val BanqueAssuranceMutuelle = ReportCategory("Banque / Assurance / Mutuelle")
  val ProduitsObjets = ReportCategory("Produits / Objets")
  val TravauxRenovations = ReportCategory("Travaux / Rénovation")
  val VoyageLoisirs = ReportCategory("Voyage / Loisirs")
  val Immobilier = ReportCategory("Immobilier")
  val Sante = ReportCategory("Secteur de la santé")
  val VoitureVehicule = ReportCategory("Voiture / Véhicule")
  val Animaux = ReportCategory("Animaux")
  val DemarchesAdministratives = ReportCategory("Démarches administratives")

  def fromValue(v: String) = {
    List(
      Covid, CafeRestaurant, AchatMagasin, Service, TelEauGazElec, BanqueAssuranceMutuelle, ProduitsObjets,
      TravauxRenovations, VoyageLoisirs, Immobilier, Sante, VoitureVehicule, Animaux, DemarchesAdministratives
    ).find(_.value == v).head
  }

  implicit val reads = new Reads[ReportCategory] {
    def reads(json: JsValue): JsResult[ReportCategory] = json.validate[String].map(fromValue(_))
  }
  implicit val writes = new Writes[ReportCategory] {
    def writes(kind: ReportCategory) = Json.toJson(kind.value)
  }
}
