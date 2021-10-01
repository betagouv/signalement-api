package models.website

import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

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
