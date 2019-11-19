package models

import java.time.OffsetDateTime
import java.util.UUID

import com.github.tminglei.slickpg.composite.Struct
import play.api.libs.json.{Json, OFormat, Writes}
import utils.Constants.ReportStatus._
import utils.Constants.Departments
import utils.{Constants, EmailAddress}

case class Report(
                   id: Option[UUID],
                   category: String,
                   subcategories: List[String],
                   details: List[DetailInputValue],
                   companyId: Option[UUID],
                   companyName: String,
                   companyAddress: String,
                   companyPostalCode: Option[String],
                   companySiret: Option[String],
                   creationDate: Option[OffsetDateTime],
                   firstName: String,
                   lastName: String,
                   email: EmailAddress,
                   contactAgreement: Boolean,
                   employeeConsumer: Boolean,
                   files: List[ReportFile],
                   status: Option[ReportStatusValue]
                 ) {
  def isEligible = {
    !employeeConsumer &&
      companyPostalCode.map(postalCode => Departments.AUTHORIZED.contains(postalCode.slice(0, 2))).getOrElse(false);
  }

  def initialStatus() = {
    (companyPostalCode, employeeConsumer) match {
      case (Some(postalCode), _) if !Departments.AUTHORIZED.contains(postalCode.slice(0, 2)) => NA
      case (_, true) => EMPLOYEE_REPORT
      case (_, _) => A_TRAITER
    }
  }
}

object Report {

  implicit val reportReader = Json.reads[Report]

  implicit def writer(implicit userRole: Option[UserRole] = None) = new Writes[Report] {
    def writes(report: Report) =
      Json.obj(
        "id" -> report.id,
        "category" -> report.category,
        "subcategories" -> report.subcategories,
        "details" -> report.details,
        "companyName" -> report.companyName,
        "companyAddress" -> report.companyAddress,
        "companyPostalCode" -> report.companyPostalCode,
        "companySiret" -> report.companySiret,
        "creationDate" -> report.creationDate,
        "contactAgreement" -> report.contactAgreement,
        "files" -> report.files,
        "status" -> report.status
      ) ++ ((userRole, report.contactAgreement) match {
        case (Some(UserRoles.Pro), false) => Json.obj()
        case (_, _) => Json.obj(
          "firstName" -> report.firstName,
          "lastName" -> report.lastName,
          "email" -> report.email
        )
      })
  }
}


case class  DetailInputValue (
                           label: String,
                           value: String
                 ) extends Struct

object DetailInputValue {
  implicit val detailInputValueFormat: OFormat[DetailInputValue] = Json.format[DetailInputValue]

  implicit def string2detailInputValue(input: String): DetailInputValue = {
    input match {
      case input if input.contains(':') => DetailInputValue(input.substring(0, input.indexOf(':') + 1), input.substring(input.indexOf(':') + 1).trim)
      case input => DetailInputValue("Précision :", input)
    }
  }
}

case class CompanyWithNbReports(companySiret: String, companyPostalCode: String, companyName: String, companyAddress: String, count: Int)

object CompanyWithNbReports {

  implicit val companyWithNbReportsWrites = new Writes[CompanyWithNbReports] {
    def writes(company: CompanyWithNbReports) = Json.obj(
      "companyPostalCode" -> company.companyPostalCode,
      "companySiret" -> company.companySiret,
      "companyName" -> company.companyName,
      "companyAddress" -> company.companyAddress,
      "count" -> company.count
    )
  }
}
