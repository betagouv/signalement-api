package models.access

import play.api.libs.json.Format
import play.api.libs.json.Json

import java.time.LocalDate

case class ProInactiveAccountRateStat(count: Float, date: LocalDate)

object ProInactiveAccountRateStat {
  implicit val ProAccountActivationRateStatFormat: Format[ProInactiveAccountRateStat] =
    Json.format[ProInactiveAccountRateStat]
}
