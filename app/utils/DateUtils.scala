package utils

import java.time.{ LocalDate, LocalDateTime }
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

object DateUtils {

  val DATE_FORMAT = "yyyy-MM-dd"
  val FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT)

  def parseDate(source: Option[String]): Option[LocalDateTime] = {

    try {
      source.map(s => LocalDate.parse(s, FORMATTER).atStartOfDay())
    } catch {
      case _: DateTimeParseException => None
    }
  }

  def parseEndDate(source: Option[String]): Option[LocalDateTime] = {
    Some(DateUtils.parseDate(source).getOrElse(LocalDateTime.now).plusDays(1))
  }


}
