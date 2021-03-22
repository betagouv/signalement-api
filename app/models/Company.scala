package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.json._
import utils.{Address, SIRET}


sealed case class AccessLevel(value: String)

object AccessLevel {
  val NONE = AccessLevel("none")
  val MEMBER = AccessLevel("member")
  val ADMIN = AccessLevel("admin")

  def fromValue(v: String) = {
    List(NONE, MEMBER, ADMIN).find(_.value == v).getOrElse(NONE)
  }
  implicit val reads = new Reads[AccessLevel] {
    def reads(json: JsValue): JsResult[AccessLevel] = json.validate[String].map(fromValue(_))
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
                  postalCode: Option[String],
                  activityCode: Option[String]
                ) {
  def shortId = this.id.toString.substring(0, 13).toUpperCase
}

case class CompanyCreation(
  siret: SIRET,
  name: String,
  address: Address,
  postalCode: Option[String],
  activityCode: Option[String]
) {
  def toCompany(): Company = Company(
    siret = siret,
    name = name,
    address = address,
    postalCode = postalCode,
    activityCode = activityCode,
  )
}

object CompanyCreation {

  implicit val format: OFormat[CompanyCreation] = Json.format[CompanyCreation]
}

object Company {
  implicit val companyFormat: OFormat[Company] = Json.format[Company]
}

case class CompanyAddressUpdate(
                  address: Address,
                  postalCode: String,
                  activationDocumentRequired: Boolean = false
                )

object CompanyAddressUpdate {
  implicit val format: OFormat[CompanyAddressUpdate] = Json.format[CompanyAddressUpdate]
}

case class UndeliveredDocument(returnedDate: OffsetDateTime)

object UndeliveredDocument {
  implicit val format: OFormat[UndeliveredDocument] = Json.format[UndeliveredDocument]
}
