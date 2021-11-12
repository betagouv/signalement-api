package models.access

import play.api.libs.json.Format
import play.api.libs.json.Json

import java.time.LocalDate

case class ProAccountActivationRateStat(count: Float, date: LocalDate)

object ProAccountActivationRateStat {
  implicit val ProAccountActivationRateStatFormat: Format[ProAccountActivationRateStat] =
    Json.format[ProAccountActivationRateStat]
}
