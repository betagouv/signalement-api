package models

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.{JsResult, JsValue, Json, Reads, Writes}

case class AsyncFile(
  id: UUID,
  userId: UUID,
  creationDate: OffsetDateTime,
  filename: Option[String],
  kind: AsyncFileKind,
  storageFilename: Option[String]
)

sealed case class AsyncFileKind(value: String)

object AsyncFileKind {
  val ReportedPhones = AsyncFileKind("ReportedPhones")
  val Reports = AsyncFileKind("Reports")
  val ReportedWebsites = AsyncFileKind("ReportedWebsites")

  val values = List(ReportedPhones, Reports, ReportedWebsites)

  def fromValue(v: String) = {
    values.find(_.value == v).head
  }
  implicit val reads = new Reads[AsyncFileKind] {
    def reads(json: JsValue): JsResult[AsyncFileKind] = json.validate[String].map(fromValue)
  }
  implicit val writes = new Writes[AsyncFileKind] {
    def writes(kind: AsyncFileKind) = Json.toJson(kind.value)
  }
}