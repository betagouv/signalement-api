package models.report.extract

import models.report.ReportFileApi
import play.twirl.api.Html

sealed trait ZipElement

object ZipElement {
  case class ZipReport(html: Html)              extends ZipElement
  case class ZipReportFile(file: ReportFileApi) extends ZipElement
}
