package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._

case class SIRET(value: String) {
  override def toString = value
}

object SIRET {
  def apply(value: String) = new SIRET(value.replaceAll("\\s", ""))
  implicit val EmailColumnType = MappedColumnType.base[SIRET, String](
    _.value,
    SIRET(_)
  )
  implicit val emailWrites = new Writes[SIRET] {
    def writes(o: SIRET): JsValue = {
      JsString(o.value)
    }
  }
  implicit val emailReads = new Reads[SIRET] {
    def reads(json: JsValue): JsResult[SIRET] = json.validate[String].map(SIRET(_))
  }
}
