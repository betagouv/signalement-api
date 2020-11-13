package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.json._
import utils.URL

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

case class Website(
  id: UUID,
  creationDate: OffsetDateTime,
  host: String,
  companyId: UUID,
  kind: WebsiteKind
)
