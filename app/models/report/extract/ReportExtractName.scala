package models.report.extract

import models.report.ReportFilter
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class ReportExtractName(value: String) extends AnyVal

object ReportExtractName {
  implicit val ReportExtractNameFormat: OFormat[ReportExtractName] = Json.format[ReportExtractName]

  def apply(reportFilter: ReportFilter) = {

    def addPrefix(prefix: String, s: Option[String]) =
      s.map(value => s"${prefix}_$value").getOrElse("")

    val tags = addPrefix(
      "tags",
      Option.when(reportFilter.withTags.nonEmpty)(reportFilter.withTags.map(_.entryName).mkString("_"))
    )
    val departments =
      addPrefix("departments", Option.when(reportFilter.departments.nonEmpty)(reportFilter.departments.mkString("_")))
    val siren =
      addPrefix("siren", Option.when(reportFilter.siretSirenList.nonEmpty)(reportFilter.siretSirenList.mkString("_")))
    val category = addPrefix("category", reportFilter.category)

    val name = List(departments, siren, category, tags).mkString

    val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yy")
    val datePart      = LocalDate.now().format(dateFormatter)

    val finalName = if (name.nonEmpty && name.length < 60) {
      s"${name.trim}.xlsx"
    } else {
      "export.xlsx"
    }

    new ReportExtractName(s"${datePart}_$finalName")
  }

}
