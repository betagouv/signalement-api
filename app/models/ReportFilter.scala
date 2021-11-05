package models

import play.api.libs.json.Json
import utils.DateUtils

import java.time.LocalDate

case class ReportFilter(
    departments: Seq[String] = Nil,
    email: Option[String] = None,
    websiteURL: Option[String] = None,
    phone: Option[String] = None,
    websiteExists: Option[Boolean] = None,
    phoneExists: Option[Boolean] = None,
    siretSirenList: Seq[String] = Nil,
    companyName: Option[String] = None,
    companyCountries: Seq[String] = Nil,
    start: Option[LocalDate] = None,
    end: Option[LocalDate] = None,
    category: Option[String] = None,
    status: Seq[Report2Status] = Nil,
    details: Option[String] = None,
    employeeConsumer: Option[Boolean] = None,
    hasCompany: Option[Boolean] = None,
    tags: Seq[String] = Nil,
    activityCodes: Seq[String] = Nil
)

case class ReportFilterBody(
    departments: Option[Seq[String]],
    email: Option[String],
    websiteURL: Option[String],
    phone: Option[String],
    websiteExists: Option[Boolean] = None,
    phoneExists: Option[Boolean] = None,
    siretSirenList: Seq[String] = Nil,
    start: Option[String],
    end: Option[String],
    category: Option[String],
    status: Seq[String] = Nil,
    details: Option[String],
    hasCompany: Option[Boolean],
    tags: Seq[String] = Nil
) {
  def toReportFilter(userRole: UserRole): ReportFilter =
    ReportFilter(
      departments = departments.getOrElse(Seq()),
      email = email,
      websiteURL = websiteURL,
      phone = phone,
      websiteExists = websiteExists,
      phoneExists = phoneExists,
      siretSirenList = siretSirenList.map(_.replaceAll("\\s", "")),
      companyName = None,
      companyCountries = Seq(),
      start = DateUtils.parseDate(start),
      end = DateUtils.parseDate(end),
      category = category,
      status = status.map(Report2Status.withName),
      details = details,
      employeeConsumer = userRole match {
        case UserRoles.Pro => Some(false)
        case _             => None
      },
      hasCompany = hasCompany,
      tags = tags
    )
}

object ReportFilterBody {
  implicit val reads = Json.using[Json.MacroOptions with Json.DefaultValues].reads[ReportFilterBody]

}
