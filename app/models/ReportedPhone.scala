package models

import java.time.OffsetDateTime
import java.util.UUID
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.{Address, SIRET}

sealed case class ReportedPhoneStatus(value: String)

object ReportedPhoneStatus {
  val VALIDATED = ReportedPhoneStatus("VALIDATED")
  val PENDING = ReportedPhoneStatus("PENDING")

  val values = List(VALIDATED, PENDING)

  def fromValue(v: String) = {
    values.find(_.value == v).head
  }

  implicit val reads = new Reads[ReportedPhoneStatus] {
    def reads(json: JsValue): JsResult[ReportedPhoneStatus] = json.validate[String].map(fromValue)
  }

  implicit val writes = new Writes[ReportedPhoneStatus] {
    def writes(status: ReportedPhoneStatus) = Json.toJson(status.value)
  }
}

case class ReportedPhoneUpdateCompany (
  companyName: String,
  companyAddress: Address,
  companySiret: SIRET,
  companyPostalCode: Option[String],
  companyActivityCode: Option[String],
)

object ReportedPhoneUpdateCompany {
  implicit val format: OFormat[ReportedPhoneUpdateCompany] = Json.format[ReportedPhoneUpdateCompany]
}

case class ReportedPhoneCreate (
  phone: String,
  companyName: String,
  companyAddress: Address,
  companySiret: SIRET,
  companyPostalCode: Option[String],
  companyActivityCode: Option[String],
)

object ReportedPhoneCreate {
  implicit val format: OFormat[ReportedPhoneCreate] = Json.format[ReportedPhoneCreate]
}

case class ReportedPhoneUpdate (
  phone: Option[String],
  companyId: Option[UUID],
  status: Option[ReportedPhoneStatus]
) {
  def mergeIn(website: ReportedPhone): ReportedPhone = {
    website.copy(
      phone = phone.getOrElse(website.phone),
      companyId = companyId.getOrElse(website.companyId),
      status = status.getOrElse(website.status),
    )
  }
}

object ReportedPhoneUpdate {
  implicit val format: OFormat[ReportedPhoneUpdate] = Json.format[ReportedPhoneUpdate]
}

case class ReportedPhone(
  id: UUID = UUID.randomUUID(),
  creationDate: OffsetDateTime = OffsetDateTime.now,
  phone: String,
  companyId: UUID,
  status: ReportedPhoneStatus = ReportedPhoneStatus.PENDING
)

object ReportedPhone {

  implicit val websiteWrites: Writes[ReportedPhone] = (
    (JsPath \ "id").write[UUID] and
    (JsPath \ "creationDate").write[OffsetDateTime] and
    (JsPath \ "phone").write[String] and
    (JsPath \ "companyId").write[UUID] and
    (JsPath \ "status").write[ReportedPhoneStatus]
  )((w: ReportedPhone) => (w.id, w.creationDate, w.phone, w.companyId, w.status))
}

object ReportedPhoneCompanyFormat {

  implicit def websiteCompany: Writes[(ReportedPhone, Company)] = (website: (ReportedPhone, Company)) => {
    val form_json = Json.toJson(website._1).as[JsObject]
    form_json + ("company" -> Json.toJson(website._2))
  }
}
