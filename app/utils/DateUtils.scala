package utils

import java.time.{ LocalDate, LocalDateTime }
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

object DateUtils {

  val DATE_FORMAT = "yyyy-MM-dd"
  val FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT)
  val TIME_FORMAT= "yyyy-MM-dd HH:mm:ss"
  val TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT)

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


  def formatTime(time: LocalDateTime) = {
    time.format(TIME_FORMATTER)
  }

  def formatTime(time: Option[LocalDateTime]) = {

    time match {
      case None => ""
      case Some(value) => value.format(TIME_FORMATTER)
    }

  }

  def getOriginDate() = {
    LocalDate.parse("2019-01-01", FORMATTER).atStartOfDay
  }

}
