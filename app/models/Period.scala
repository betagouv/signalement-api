package models

trait Period {}

case object Day extends Period
case object Week extends Period
case object Month extends Period

object Period {
  def fromString(period: String): Period =
    period match {
      case "day"   => Day
      case "week"  => Week
      case "month" => Month
      case _       => Month
    }
}
