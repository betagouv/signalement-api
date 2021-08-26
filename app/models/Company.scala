package models

import play.api.libs.json._
import utils.SIRET

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

sealed case class AccessLevel(value: String)

object AccessLevel {
  val NONE = AccessLevel("none")
  val MEMBER = AccessLevel("member")
  val ADMIN = AccessLevel("admin")

  def fromValue(v: String) =
    List(NONE, MEMBER, ADMIN).find(_.value == v).getOrElse(NONE)
  implicit val reads = new Reads[AccessLevel] {
    def reads(json: JsValue): JsResult[AccessLevel] = json.validate[String].map(fromValue)
  }
  implicit val writes = new Writes[AccessLevel] {
    def writes(level: AccessLevel) = Json.toJson(level.value)
  }
}

case class UserAccess(
    companyId: UUID,
    userId: UUID,
    level: AccessLevel,
    updateDate: OffsetDateTime
)

case class Company(
    id: UUID = UUID.randomUUID(),
    siret: SIRET,
    creationDate: OffsetDateTime = OffsetDateTime.now,
    name: String,
    address: Address,
    activityCode: Option[String]
) {
  def shortId = this.id.toString.substring(0, 13).toUpperCase
}

case class CompanyCreation(
    siret: SIRET,
    name: String,
    address: Address,
    activityCode: Option[String]
) {
  def toCompany(): Company = Company(
    siret = siret,
    name = name,
    address = address,
    activityCode = activityCode
  )
}

object CompanyCreation {

  implicit val format: OFormat[CompanyCreation] = Json.format[CompanyCreation]
}

object Company {

  implicit val companyFormat: OFormat[Company] = Json.format[Company]

  implicit def writes: Writes[(Company, AccessLevel)] = (companyAccessLevel: (Company, AccessLevel)) => {
    val companyJson = Json.toJson(companyAccessLevel._1).as[JsObject]
    companyJson + ("level" -> Json.toJson(companyAccessLevel._2))
  }
}

case class CompanyWithNbReports(company: Company, count: Int)

object CompanyWithNbReports {

  implicit def writes: Writes[CompanyWithNbReports] = (data: CompanyWithNbReports) => {
    val companyJson = Json.toJson(data.company).as[JsObject]
    companyJson + ("count" -> Json.toJson(data.count))
  }
}

case class CompanyAddressUpdate(
    address: Address,
    activationDocumentRequired: Boolean = false
)

object CompanyAddressUpdate {
  implicit val format: OFormat[CompanyAddressUpdate] = Json.format[CompanyAddressUpdate]
}

case class UndeliveredDocument(returnedDate: LocalDate)

object UndeliveredDocument {
  implicit val format: OFormat[UndeliveredDocument] = Json.format[UndeliveredDocument]
}
