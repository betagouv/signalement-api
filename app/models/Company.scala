package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.json._
import utils.SIRET


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
                  id: UUID,
                  siret: SIRET,
                  creationDate: OffsetDateTime,
                  name: String,
                  address: String,
                  postalCode: Option[String],
                )

object Company {
  implicit val companyWrites = new Writes[Company] {
    def writes(company: Company) = Json.obj(
      "id" -> company.id,
      "siret" -> company.siret,
      "name"  -> company.name,
      "address"  -> company.address,
      "postalCode"  -> company.postalCode
    )
  }
}
