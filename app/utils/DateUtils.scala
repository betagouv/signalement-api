package utils

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

object DateUtils {

  val DATE_FORMAT = "yyyy-MM-dd"

  def parseDate(source: Option[String]): Option[LocalDateTime] = {

    val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT)

    try {
      source.map(s => LocalDate.parse(s, formatter).atStartOfDay())
    } catch {
      case e: DateTimeParseException => None
    }
  }

}
