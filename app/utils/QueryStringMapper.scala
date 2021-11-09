package utils

import java.time.LocalDate

class QueryStringMapper(q: Map[String, Seq[String]]) {

  def long(k: String): Option[Long] = string(k).map(_.toLong)

  def int(k: String): Option[Int] = string(k).map(_.toInt)

  def string(k: String): Option[String] = q.get(k).flatMap(_.headOption)

  def seq(k: String): Seq[String] = q.getOrElse(k, Nil)

  def localDate(k: String): Option[LocalDate] = DateUtils.parseDate(string(k))

  def boolean(k: String): Option[Boolean] = string(k) match {
    case Some("true")  => Some(true)
    case Some("false") => Some(false)
    case _             => None
  }
}
