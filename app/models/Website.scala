package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.json._
import utils.URL

sealed case class WebsiteKind(value: String)

object WebsiteKind {
  val DEFAULT = WebsiteKind("DEFAULT")
  val MARKETPLACE = WebsiteKind("MARKETPLACE")
  val PENDING = WebsiteKind("PENDING")

  def fromValue(v: String) = {
    List(MARKETPLACE, DEFAULT, PENDING).find(_.value == v).head
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
  companyId: Option[UUID],
  kind: WebsiteKind
)
