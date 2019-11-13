package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._

case class EmailAddress(value: String)

object EmailAddress {
  def apply(value: String) = new EmailAddress(value.trim.toLowerCase)
  implicit val EmailColumnType = MappedColumnType.base[EmailAddress, String](
    _.value,
    EmailAddress(_)
  )
  implicit val emailWrites = new Writes[EmailAddress] {
    def writes(o: EmailAddress): JsValue = {
      JsString(o.value)
    }
  }
  implicit val emailReads = new Reads[EmailAddress] {
    def reads(json: JsValue): JsResult[EmailAddress] = json.validate[String].map(EmailAddress(_))
  }
}
