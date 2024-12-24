package models.report

import play.api.libs.json.Format
import play.api.libs.json.Json
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.H2Profile.MappedColumnType
import slick.jdbc.JdbcType

case class ConsumerIp(value: String) extends AnyVal

object ConsumerIp {
  implicit val ConsumerIpFormat: Format[ConsumerIp] = Json.valueFormat[ConsumerIp]

  implicit val ConsumerIpColumnType: JdbcType[ConsumerIp] with BaseTypedType[ConsumerIp] =
    MappedColumnType.base[ConsumerIp, String](
      _.value,
      ConsumerIp(_)
    )

}
