package models

import play.api.libs.json._
import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID

sealed case class WebsiteKind(value: String, isExclusive: Boolean)

object WebsiteKind {
  val DEFAULT = WebsiteKind("DEFAULT", true)
  val MARKETPLACE = WebsiteKind("MARKETPLACE", true)
  val PENDING = WebsiteKind("PENDING", false)

  val values = List(DEFAULT, MARKETPLACE, PENDING)

  def fromValue(v: String) =
    values.find(_.value == v).head
  implicit val reads = new Reads[WebsiteKind] {
    def reads(json: JsValue): JsResult[WebsiteKind] = json.validate[String].map(fromValue(_))
  }
  implicit val writes = new Writes[WebsiteKind] {
    def writes(kind: WebsiteKind) = Json.toJson(kind.value)
  }
}

case class WebsiteUpdateCompany(
    companyName: String,
    companyAddress: Address,
    companySiret: SIRET,
    companyActivityCode: Option[String]
)

object WebsiteUpdateCompany {
  implicit val format: OFormat[WebsiteUpdateCompany] = Json.format[WebsiteUpdateCompany]
}

case class WebsiteCreate(
    host: String,
    companyName: String,
    companyAddress: Address,
    companySiret: SIRET,
    companyPostalCode: Option[String],
    companyActivityCode: Option[String]
)

object WebsiteCreate {
  implicit val format: OFormat[WebsiteCreate] = Json.format[WebsiteCreate]
}

case class WebsiteUpdate(
    host: Option[String],
    companyId: Option[UUID],
    kind: Option[WebsiteKind]
) {
  def mergeIn(website: Website): Website =
    website.copy(
      host = host.getOrElse(website.host),
      companyId = companyId,
      kind = kind.getOrElse(website.kind)
    )
}

object WebsiteUpdate {
  implicit val format: OFormat[WebsiteUpdate] = Json.format[WebsiteUpdate]
}

case class Website(
    id: UUID = UUID.randomUUID(),
    creationDate: OffsetDateTime = OffsetDateTime.now,
    host: String,
    country: Option[String],
    companyId: Option[UUID],
    kind: WebsiteKind = WebsiteKind.PENDING
)

object Website {
  implicit val WebsiteWrites: Writes[Website] = Json.writes[Website]
}

object WebsiteCompanyFormat {

  implicit def websiteCompany: Writes[(Website, Company)] = (tuple: (Website, Company)) => {
    val website_json = Json.toJson(tuple._1).as[JsObject]
    website_json + ("company" -> Json.toJson(tuple._2))
  }

  implicit def websiteCompanyCount: Writes[(Website, Company, Int)] = (tuple: (Website, Company, Int)) => {
    val website_json = Json.toJson(tuple._1).as[JsObject]
    website_json + ("company" -> Json.toJson(tuple._2)) + ("count" -> Json.toJson(tuple._3))
  }
}
