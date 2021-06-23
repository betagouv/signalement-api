package utils

import play.api.libs.json.{JsResult, JsString, JsValue, Reads, Writes}
import repositories.PostgresProfile.api._

case class Address(value: String) {
  override def toString = value
}

object Address {
  def apply(value: String) = {
    val distinctLines = value.split(" - ").distinct.map(_.trim)
    new Address(
      distinctLines
        .filterNot(_ == "FRANCE")
        .filterNot(line => distinctLines.count(_.contains(line)) > 1)
        .mkString(" - ")
    )
  }

  implicit val AddressColumnType = MappedColumnType.base[Address, String](
    _.value,
    Address(_)
  )
  implicit val addressWrites = new Writes[Address] {
    def writes(o: Address): JsValue = {
      JsString(o.value)
    }
  }
  implicit val addressReads = new Reads[Address] {
    def reads(json: JsValue): JsResult[Address] = json.validate[String].map(Address(_))
  }
}


