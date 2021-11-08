package models

import utils.QueryStringMapper

import java.time.LocalDate
import scala.util.Try

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
    status: Seq[ReportStatus] = Nil,
    details: Option[String] = None,
    employeeConsumer: Option[Boolean] = None,
    hasCompany: Option[Boolean] = None,
    tags: Seq[String] = Nil,
    activityCodes: Seq[String] = Nil
)

object ReportFilter {
  def fromQueryString(q: Map[String, Seq[String]], userRole: UserRole): Try[ReportFilter] = Try {
    val mapper = new QueryStringMapper(q)
    ReportFilter(
      departments = mapper.seq("departments"),
      email = mapper.string("email"),
      websiteURL = mapper.string("websiteURL"),
      phone = mapper.string("phone"),
      websiteExists = mapper.boolean("websiteExists"),
      phoneExists = mapper.boolean("phoneExists"),
      siretSirenList = mapper.seq("siretSirenList"),
      companyName = mapper.string("companyName"),
      companyCountries = mapper.seq("companyCountries"),
      start = mapper.localDate("start"),
      end = mapper.localDate("end"),
      category = mapper.string("category"),
      status = ReportStatus.filterByUserRole(
        mapper.seq("status").map(ReportStatus.withName),
        userRole
      ),
      details = mapper.string("details"),
      employeeConsumer = userRole match {
        case UserRoles.Pro => Some(false)
        case _             => None
      },
      hasCompany = mapper.boolean("hasCompany"),
      tags = mapper.seq("tags"),
      activityCodes = mapper.seq("activityCode")
    )
  }
}
