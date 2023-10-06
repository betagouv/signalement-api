package repositories.companyaccess

import models.company.AccessLevel
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object CompanyAccessColumnType {
  implicit val AccessLevelColumnType: JdbcType[AccessLevel] with BaseTypedType[AccessLevel] =
    MappedColumnType.base[AccessLevel, String](_.value, AccessLevel.fromValue)
}
