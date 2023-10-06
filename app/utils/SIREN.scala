package utils

import play.api.libs.json._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

case class SIREN private (value: String) extends AnyVal {
  override def toString = value
}

object SIREN {

  val length = 9

  def fromUnsafe(value: String) = new SIREN(value.replaceAll("\\s", ""))

  def fromSIRET(siret: SIRET) = new SIREN(siret.value.substring(0, 9))

  def pattern = s"[0-9]{$length}"

  def isValid(siren: String): Boolean = siren.matches(SIREN.pattern)

  implicit val sirenColumnType: JdbcType[SIREN] with BaseTypedType[SIREN] = MappedColumnType.base[SIREN, String](
    _.value,
    SIREN.fromUnsafe
  )
  implicit val sirenListColumnType: JdbcType[List[SIREN]] with BaseTypedType[List[SIREN]] =
    MappedColumnType.base[List[SIREN], List[String]](
      _.map(_.value),
      _.map(SIREN.fromUnsafe)
    )
  implicit val sirenWrites: Writes[SIREN] = Json.valueWrites[SIREN]
  implicit val sirenReads: Reads[SIREN] = Reads.StringReads.map(SIREN.fromUnsafe) // To use the apply method
}
