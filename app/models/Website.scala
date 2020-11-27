package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.{Address, SIRET}

sealed case class WebsiteKind(value: String, isExlusive: Boolean)

object WebsiteKind {
  val DEFAULT = WebsiteKind("DEFAULT", false)
  val MARKETPLACE = WebsiteKind("MARKETPLACE", true)
  val PENDING = WebsiteKind("PENDING", false)
  val EXCLUSIVE = WebsiteKind("EXCLUSIVE", true)

  val values = List(DEFAULT, MARKETPLACE, PENDING, EXCLUSIVE)

  def fromValue(v: String) = {
    values.find(_.value == v).head
  }
  implicit val reads = new Reads[WebsiteKind] {
    def reads(json: JsValue): JsResult[WebsiteKind] = json.validate[String].map(fromValue(_))
  }
  implicit val writes = new Writes[WebsiteKind] {
    def writes(kind: WebsiteKind) = Json.toJson(kind.value)
  }
}

case class WebsiteUpdateCompany (
  companyName: String,
  companyAddress: Address,
  companySiret: SIRET,
  companyPostalCode: Option[String],
  companyActivityCode: Option[String],
)

object WebsiteUpdateCompany {
  implicit val format: OFormat[WebsiteUpdateCompany] = Json.format[WebsiteUpdateCompany]
}

case class WebsiteCreate (
  host: String,
  companyName: String,
  companyAddress: Address,
  companySiret: SIRET,
  companyPostalCode: Option[String],
  companyActivityCode: Option[String],
)

object WebsiteCreate {
  implicit val format: OFormat[WebsiteCreate] = Json.format[WebsiteCreate]
}

case class WebsiteUpdate (
  host: Option[String],
  companyId: Option[UUID],
  kind: Option[WebsiteKind]
) {
  def mergeIn(website: Website): Website = {
    website.copy(
      host = host.getOrElse(website.host),
      companyId = companyId.getOrElse(website.companyId),
      kind = kind.getOrElse(website.kind),
    )
  }
}

object WebsiteUpdate {
  implicit val format: OFormat[WebsiteUpdate] = Json.format[WebsiteUpdate]
}

case class Website(
  id: UUID = UUID.randomUUID(),
  creationDate: OffsetDateTime = OffsetDateTime.now,
  host: String,
  companyId: UUID,
  kind: WebsiteKind = WebsiteKind.PENDING
) {
  def toJsonWithCompany(company: Option[Company]): JsObject = {
    Json.toJson(this).as[JsObject].deepMerge(Json.obj("company" -> company))
  }
}

object Website {

  implicit val websiteWrites: Writes[Website] = (
    (JsPath \ "id").write[UUID] and
    (JsPath \ "creationDate").write[OffsetDateTime] and
    (JsPath \ "host").write[String] and
    (JsPath \ "companyId").write[UUID] and
    (JsPath \ "kind").write[WebsiteKind]
  )((w: Website) => (w.id, w.creationDate, w.host, w.companyId, w.kind))
}