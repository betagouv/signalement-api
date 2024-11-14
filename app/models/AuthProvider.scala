package models

import enumeratum._
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

sealed trait AuthProvider extends EnumEntry
object AuthProvider extends PlayEnum[AuthProvider] {
  val values = findValues

  case object ProConnect  extends AuthProvider
  case object SignalConso extends AuthProvider

  implicit val AuthProviderColumnType: JdbcType[AuthProvider] with BaseTypedType[AuthProvider] =
    MappedColumnType.base[AuthProvider, String](
      _.entryName,
      AuthProvider.namesToValuesMap
    )
}
