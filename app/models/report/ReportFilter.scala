package models.report

import models.UserRole
import models.UserRole.Admin
import models.UserRole.DGCCRF
import utils.QueryStringMapper

import java.time.LocalDate
import java.util.UUID
import scala.util.Try

case class ReportFilter(
    departments: Seq[String] = Seq.empty,
    email: Option[String] = None,
    websiteURL: Option[String] = None,
    phone: Option[String] = None,
    siretSirenList: Seq[String] = Seq.empty,
    companyIds: Seq[UUID] = Seq.empty,
    companyName: Option[String] = None,
    companyCountries: Seq[String] = Seq.empty,
    start: Option[LocalDate] = None,
    end: Option[LocalDate] = None,
    category: Option[String] = None,
    status: Seq[ReportStatus] = Seq.empty,
    details: Option[String] = None,
    employeeConsumer: Option[Boolean] = None,
    hasForeignCountry: Option[Boolean] = None,
    hasWebsite: Option[Boolean] = None,
    hasPhone: Option[Boolean] = None,
    hasCompany: Option[Boolean] = None,
    tags: Seq[ReportTagFilter] = Seq.empty,
    activityCodes: Seq[String] = Seq.empty
)

object ReportFilter {
  def fromQueryString(q: Map[String, Seq[String]], userRole: UserRole): Try[ReportFilter] = Try {
    val mapper = new QueryStringMapper(q)
    ReportFilter(
      departments = mapper.seq("departments"),
      email = mapper.string("email"),
      websiteURL = mapper.string("websiteURL"),
      phone = mapper.string("phone"),
      siretSirenList = mapper.seq("siretSirenList"),
      companyName = mapper.string("companyName"),
      companyCountries = mapper.seq("companyCountries"),
      start = mapper.localDate("start"),
      end = mapper.localDate("end"),
      category = mapper.string("category"),
      companyIds = mapper.seq("companyIds").map(UUID.fromString),
      status = ReportStatus.filterByUserRole(
        mapper.seq("status").map(ReportStatus.withName),
        userRole
      ),
      details = mapper.string("details"),
      employeeConsumer = userRole match {
        case Admin  => None
        case DGCCRF => None
        case _      => Some(false)
      },
      hasForeignCountry = mapper.boolean("hasForeignCountry"),
      hasWebsite = mapper.boolean("hasWebsite"),
      hasPhone = mapper.boolean("hasPhone"),
      hasCompany = mapper.boolean("hasCompany"),
      tags = mapper.seq("tags").map(ReportTagFilter.withName(_)),
      activityCodes = mapper.seq("activityCodes")
    )
  }
}
