package models.company

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.UserRole
import play.api.libs.json._
import utils.QueryStringMapper
import utils.SIREN
import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Try

case class AccessLevel(value: String) extends AnyVal

object AccessLevel {
  val NONE   = AccessLevel("none")
  val MEMBER = AccessLevel("member")
  val ADMIN  = AccessLevel("admin")

  def fromValue(v: String) =
    List(NONE, MEMBER, ADMIN).find(_.value == v).getOrElse(NONE)
  implicit val reads: Reads[AccessLevel]   = Json.valueReads[AccessLevel]
  implicit val writes: Writes[AccessLevel] = Json.valueWrites[AccessLevel]
}

case class UserAccess(
    companyId: UUID,
    userId: UUID,
    level: AccessLevel,
    updateDate: OffsetDateTime,
    creationDate: OffsetDateTime
)

case class Company(
    id: UUID = UUID.randomUUID(),
    siret: SIRET,
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    name: String,
    address: Address,
    activityCode: Option[String],
    isHeadOffice: Boolean,
    isOpen: Boolean,
    isPublic: Boolean,
    brand: Option[String],
    commercialName: Option[String],
    establishmentCommercialName: Option[String],
    albertActivityLabel: Option[String] = None,
    albertUpdateDate: Option[OffsetDateTime] = None
) {

  def siren: SIREN = SIREN.fromSIRET(this.siret)
}

case class CompanyRegisteredSearch(
    departments: Seq[String] = Seq.empty[String],
    activityCodes: Seq[String] = Seq.empty[String],
    identity: Option[SearchCompanyIdentity] = None,
    emailsWithAccess: Option[String] = None
)

object CompanyRegisteredSearch {
  def fromQueryString(q: Map[String, Seq[String]]): Try[CompanyRegisteredSearch] = Try {
    val mapper = new QueryStringMapper(q)
    CompanyRegisteredSearch(
      departments = mapper.seq("departments"),
      activityCodes = mapper.seq("activityCodes"),
      emailsWithAccess = mapper.string("emailsWithAccess", trimmed = true),
      identity = mapper.nonEmptyString("identity", trimmed = true).map(SearchCompanyIdentity.fromString)
    )
  }
}

object Company {

  implicit val companyFormat: OFormat[Company] = Json.format[Company]
}

case class CompanyWithAccess(
    company: Company,
    access: CompanyAccess
) {
  def isAdmin = access.level == AccessLevel.ADMIN
}

object CompanyWithAccess {

  implicit val writes: OWrites[CompanyWithAccess] = Json.writes[CompanyWithAccess]

  // legacy writes
  val writesAsCompanyWithAdditionalField: Writes[CompanyWithAccess] = (companyWithAccess: CompanyWithAccess) => {
    val companyJson = Json.toJson(companyWithAccess.company).as[JsObject]
    companyJson + ("level" -> Json.toJson(companyWithAccess.access.level))
  }

}

case class CompanyAccess(
    level: AccessLevel,
    kind: CompanyAccessKind
)
object CompanyAccess {
  implicit val writes: OWrites[CompanyAccess] = Json.writes[CompanyAccess]
}

case class CompanyWithAccessAndCounts(
    company: Company,
    access: CompanyAccess,
    reportsCount: Long,
    ongoingReportsCount: Int,
    usersCount: Option[Int]
)

object CompanyWithAccessAndCounts {

  implicit val writes: OWrites[CompanyWithAccessAndCounts] = Json.writes[CompanyWithAccessAndCounts]

}

sealed trait CompanyAccessKind extends EnumEntry

object CompanyAccessKind extends PlayEnum[CompanyAccessKind] {

  val values = findValues

  // The pro has directly access to this company, as stored in DB
  case object Direct extends CompanyAccessKind

  // When the pro has access to this company because he has a direct access to its head office
  case object Inherited extends CompanyAccessKind

  // Edge case
  // When the pro has a direct access to this company as a MEMBER
  // But also has a direct access to its head office as an ADMIN
  case object InheritedAdminAndDirectMember extends CompanyAccessKind

}

case class CompanyCreation(
    siret: SIRET,
    name: String,
    address: Address,
    activityCode: Option[String],
    isHeadOffice: Option[Boolean],
    isOpen: Option[Boolean],
    isPublic: Option[Boolean],
    brand: Option[String],
    commercialName: Option[String],
    establishmentCommercialName: Option[String]
) {
  def toCompany(): Company = Company(
    siret = siret,
    name = name,
    address = address,
    activityCode = activityCode,
    isHeadOffice = isHeadOffice.getOrElse(false),
    isOpen = isOpen.getOrElse(true),
    isPublic = isPublic.getOrElse(true),
    brand = brand,
    commercialName = commercialName,
    establishmentCommercialName = establishmentCommercialName
  )
}

object CompanyCreation {

  implicit val format: OFormat[CompanyCreation] = Json.format[CompanyCreation]
}

case class CompanyWithNbReports(
    id: UUID = UUID.randomUUID(),
    siret: SIRET,
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    name: String,
    commercialName: Option[String],
    establishmentCommercialName: Option[String],
    brand: Option[String],
    address: Address,
    activityCode: Option[String],
    albertActivityLabel: Option[String],
    isHeadOffice: Boolean,
    isOpen: Option[Boolean],
    count: Long,
    responseRate: Int
)

object CompanyWithNbReports {
  implicit def writes(implicit userRole: Option[UserRole]): Writes[CompanyWithNbReports] =
    Json
      .writes[CompanyWithNbReports]
      .contramap(r =>
        userRole match {
          case Some(UserRole.Professionnel) =>
            r.copy(albertActivityLabel = None)
          case _ => r
        }
      )
}

case class CompanyAddressUpdate(
    address: Address,
    activationDocumentRequired: Boolean = false
)

object CompanyAddressUpdate {
  implicit val format: OFormat[CompanyAddressUpdate] = Json.format[CompanyAddressUpdate]
}
