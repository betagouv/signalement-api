package models.access

import play.api.libs.json.Format
import play.api.libs.json.Json

import java.time.LocalDate

case class CompanyFirstUserAccountActivationCountStat(count: Int, date: LocalDate)

object CompanyFirstUserAccountActivationCountStat {
  implicit val CompanyFirstCreationAccountCountStatFormat: Format[CompanyFirstUserAccountActivationCountStat] =
    Json.format[CompanyFirstUserAccountActivationCountStat]
}
