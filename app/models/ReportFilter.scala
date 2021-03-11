package models

import java.time.LocalDate

import play.api.libs.json.Json
import utils.Constants.ReportStatus.ReportStatusValue

case class ReportFilter(
  departments: Seq[String] = List(),
  email: Option[String] = None,
  websiteURL: Option[String] = None,
  phone: Option[String] = None,
  siretSirenList: List[String] = List(),
  companyName: Option[String] = None,
  companyCountries: Seq[String] = List(),
  start: Option[LocalDate] = None,
  end: Option[LocalDate] = None,
  category: Option[String] = None,
  statusList: Option[Seq[ReportStatusValue]] = None,
  details: Option[String] = None,
  employeeConsumer: Option[Boolean] = None,
  hasCompany: Option[Boolean] = None,
  tags: Seq[String] = Nil
)

case class ReportFilterBody(
  departments: Option[Seq[String]],
  email: Option[String],
  websiteURL: Option[String],
  phone: Option[String],
  siretSirenList: List[String] = List(),
  start: Option[String],
  end: Option[String],
  category: Option[String],
  status: Option[String],
  details: Option[String],
  hasCompany: Option[Boolean],
  tags: List[String] = Nil
) {
  def toReportFilter(
    start: Option[LocalDate],
    end: Option[LocalDate],
    employeeConsumer: Option[Boolean],
    statusList: Option[Seq[ReportStatusValue]]
  ): ReportFilter = {
    ReportFilter(
      departments = departments.getOrElse(Seq()),
      email = email,
      websiteURL = None,
      phone = phone,
      siretSirenList = siretSirenList,
      companyName = None,
      companyCountries = Seq(),
      start = start,
      end = end,
      category = category,
      statusList = statusList,
      details = details,
      employeeConsumer = employeeConsumer,
      hasCompany = hasCompany,
      tags = tags
    )
  }
}

object ReportFilterBody {
  implicit val reads = Json.using[Json.MacroOptions with Json.DefaultValues].reads[ReportFilterBody]

}