package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._

import scala.util.Try

case class URL private (value: String) extends AnyVal {
  override def toString = value
  def getHost: Option[String] =
    Try(new java.net.URL(value)).toOption.map(url => url.getHost.toLowerCase().replaceFirst("www\\.", ""))
}

object URL {
  def apply(value: String) = new URL(value.trim.toLowerCase.replaceFirst("^(?!https?)", "https://"))
  implicit val URLColumnType = MappedColumnType.base[URL, String](
    _.value,
    URL(_)
  )
  implicit val urlWrites: Writes[URL] = Json.valueWrites[URL]
  implicit val urlReads: Reads[URL] = Reads.StringReads.map(URL(_)) // To use the apply method
}
