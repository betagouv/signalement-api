package utils

import play.api.libs.json.Format
import play.api.libs.json.Json
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

case class CountryCode(value: String) extends AnyVal

object CountryCode {
  implicit val CountryCodeFormat: Format[CountryCode] = Json.valueFormat[CountryCode]

  implicit val CountryColumnType: JdbcType[CountryCode] with BaseTypedType[CountryCode] =
    MappedColumnType.base[CountryCode, String](
      _.value,
      CountryCode(_)
    )
}
