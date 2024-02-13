package models.report

import enumeratum.EnumEntry.Uppercase
import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.company.Address
import models.company.Company
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID

sealed trait ReportedPhoneStatus extends EnumEntry with Uppercase

object ReportedPhoneStatus extends PlayEnum[ReportedPhoneStatus] {

  case object Validated extends ReportedPhoneStatus
  case object Pending   extends ReportedPhoneStatus

  override def values: IndexedSeq[ReportedPhoneStatus] = findValues
}

case class ReportedPhoneUpdateCompany(
    companyName: String,
    companyAddress: Address,
    companySiret: SIRET,
    companyActivityCode: Option[String]
)

object ReportedPhoneUpdateCompany {
  implicit val format: OFormat[ReportedPhoneUpdateCompany] = Json.format[ReportedPhoneUpdateCompany]
}

case class ReportedPhoneCreate(
    phone: String,
    companyName: String,
    companyAddress: Address,
    companySiret: SIRET,
    companyActivityCode: Option[String]
)

object ReportedPhoneCreate {
  implicit val format: OFormat[ReportedPhoneCreate] = Json.format[ReportedPhoneCreate]
}

case class ReportedPhoneUpdate(
    phone: Option[String],
    companyId: Option[UUID],
    status: Option[ReportedPhoneStatus]
) {
  def mergeIn(reportedPhone: ReportedPhone): ReportedPhone =
    reportedPhone.copy(
      phone = phone.getOrElse(reportedPhone.phone),
      companyId = companyId.getOrElse(reportedPhone.companyId),
      status = status.getOrElse(reportedPhone.status)
    )
}

object ReportedPhoneUpdate {
  implicit val format: OFormat[ReportedPhoneUpdate] = Json.format[ReportedPhoneUpdate]
}

case class ReportedPhone(
    id: UUID = UUID.randomUUID(),
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    phone: String,
    companyId: UUID,
    status: ReportedPhoneStatus = ReportedPhoneStatus.Pending
)

object ReportedPhone {

  implicit val writes: Writes[ReportedPhone] = (
    (JsPath \ "id").write[UUID] and
      (JsPath \ "creationDate").write[OffsetDateTime] and
      (JsPath \ "phone").write[String] and
      (JsPath \ "companyId").write[UUID] and
      (JsPath \ "status").write[ReportedPhoneStatus]
  )((w: ReportedPhone) => (w.id, w.creationDate, w.phone, w.companyId, w.status))
}

object ReportedPhoneCompanyFormat {

  implicit def reportedPhoneCompany: Writes[(ReportedPhone, Company)] = (reportedPhone: (ReportedPhone, Company)) => {
    val form_json = Json.toJson(reportedPhone._1).as[JsObject]
    form_json + ("company" -> Json.toJson(reportedPhone._2))
  }

  implicit def reportedPhoneCompanyCount: Writes[(ReportedPhone, Company, Int)] =
    (tuple: (ReportedPhone, Company, Int)) => {
      val reportedPhone_json = Json.toJson(tuple._1).as[JsObject]
      reportedPhone_json + ("company" -> Json.toJson(tuple._2)) + ("count" -> Json.toJson(tuple._3))
    }
}
