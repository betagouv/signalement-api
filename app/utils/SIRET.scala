package utils

import controllers.error.AppError.MalformedSIRET
import play.api.libs.json._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

case class SIRET private (value: String) extends AnyVal {
  override def toString = value
}

object SIRET {

  val length = 14

  def apply(value: String): SIRET =
    if (value.replaceAll("\\s", "").matches(SIRET.pattern)) {
      new SIRET(value)
    } else {
      throw MalformedSIRET(value)
    }

  // deprecated
  def fromUnsafe(value: String) = new SIRET(if (value != null) value.replaceAll("\\s", "") else value)

  def pattern = s"[0-9]{$length}"

  def isValid(siret: String): Boolean = siret.matches(SIRET.pattern)

  implicit val siretColumnType: JdbcType[SIRET] with BaseTypedType[SIRET] = MappedColumnType.base[SIRET, String](
    _.value,
    SIRET.fromUnsafe
  )
  implicit val siretListColumnType: JdbcType[List[SIRET]] with BaseTypedType[List[SIRET]] =
    MappedColumnType.base[List[SIRET], List[String]](
      _.map(_.value),
      _.map(SIRET.fromUnsafe)
    )
  implicit val siretWrites: Writes[SIRET] = Json.valueWrites[SIRET]
  implicit val siretReads: Reads[SIRET]   = Reads.StringReads.map(SIRET.fromUnsafe) // To use the apply method

}
