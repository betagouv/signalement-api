package utils

import scala.util.Try
import play.api.libs.json._
import repositories.PostgresProfile.api._

case class URL(value: String) {
  override def toString = value
  def getHost = Try(new java.net.URL(value)).toOption.map(url => url.getHost.toLowerCase())
}

object URL {
  def apply(value: String) = new URL(value.trim.toLowerCase)
  implicit val URLColumnType = MappedColumnType.base[URL, String](
    _.value,
    URL(_)
  )
  implicit val urlWrites = new Writes[URL] {
    def writes(o: URL): JsValue = {
      JsString(o.value)
    }
  }
  implicit val urlReads = new Reads[URL] {
    def reads(json: JsValue): JsResult[URL] = json.validate[String].map(URL(_))
  }
}