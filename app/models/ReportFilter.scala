package models

import play.api.libs.json.Json
import utils.Constants.ReportStatus
import utils.Constants.ReportStatus.ReportStatusValue
import utils.Constants.ReportStatus.getStatusListForValueWithUserRole
import utils.DateUtils

import java.time.LocalDate
import java.util.UUID

case class ReportFilter(
    departments: Seq[String] = Nil,
    email: Option[String] = None,
    websiteURL: Option[String] = None,
    phone: Option[String] = None,
    websiteExists: Option[Boolean] = None,
    phoneExists: Option[Boolean] = None,
    siretSirenList: Seq[String] = Nil,
    companyIds: Seq[UUID] = Nil,
    companyName: Option[String] = None,
    companyCountries: Seq[String] = Nil,
    start: Option[LocalDate] = None,
    end: Option[LocalDate] = None,
    category: Option[String] = None,
    statusList: Seq[ReportStatusValue] = Nil,
    details: Option[String] = None,
    employeeConsumer: Option[Boolean] = None,
    hasCompany: Option[Boolean] = None,
    tags: Seq[String] = Nil,
    activityCodes: Seq[String] = Nil
)

object ReportFilter {
  def fromQueryString(q: Map[String, Seq[String]], userRole: UserRole): ReportFilter = {
    def parseString(k: String): Option[String] = q.get(k).flatMap(_.headOption)
    def parseArray(k: String): Seq[String] = q.getOrElse(k, Nil)
    def parseDate(k: String): Option[LocalDate] = DateUtils.parseDate(parseString(k))
    def parseBoolean(k: String): Option[Boolean] = parseString(k) match {
      case "true"  => Some(true)
      case "false" => Some(false)
      case _       => None
    }
    ReportFilter(
      departments = parseArray("departments"),
      email = parseString("email"),
      websiteURL = parseString("websiteURL"),
      phone = parseString("phone"),
      websiteExists = parseBoolean("websiteExists"),
      phoneExists = parseBoolean("phoneExists"),
      siretSirenList = parseArray("siretSirenList"),
      companyName = parseString("companyName"),
      companyCountries = parseArray("companyCountries"),
      start = parseDate("start"),
      end = parseDate("end"),
      category = parseString("category"),
      statusList = getStatusListForValueWithUserRole(parseArray("status"), userRole),
      details = parseString("details"),
      employeeConsumer = userRole match {
        case UserRoles.Pro => Some(false)
        case _             => None
      },
      hasCompany = parseBoolean("hasCompany"),
      tags = parseArray("tags"),
      activityCodes = parseArray("activityCode")
    )
  }

}

case class ReportFilterBody(
    departments: Option[Seq[String]],
    email: Option[String],
    websiteURL: Option[String],
    phone: Option[String],
    websiteExists: Option[Boolean] = None,
    phoneExists: Option[Boolean] = None,
    siretSirenList: Seq[String] = Nil,
    companyIds: Seq[String] = Nil,
    start: Option[String],
    end: Option[String],
    category: Option[String],
    status: Option[String],
    details: Option[String],
    hasCompany: Option[Boolean],
    tags: Seq[String] = Nil
) {
  def toReportFilter(
      employeeConsumer: Option[Boolean],
      statusList: Seq[ReportStatusValue]
  ): ReportFilter =
    ReportFilter(
      departments = departments.getOrElse(Seq()),
      email = email,
      websiteURL = websiteURL,
      phone = phone,
      websiteExists = websiteExists,
      phoneExists = phoneExists,
      siretSirenList = siretSirenList,
      companyName = None,
      companyCountries = Seq(),
      start = DateUtils.parseDate(start),
      end = DateUtils.parseDate(end),
      category = category,
      statusList = statusList,
      details = details,
      employeeConsumer = employeeConsumer,
      hasCompany = hasCompany,
      tags = tags
    )
}

object ReportFilterBody {
  implicit val reads = Json.using[Json.MacroOptions with Json.DefaultValues].reads[ReportFilterBody]

}
