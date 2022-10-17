package repositories.companyaccess

import models.company.AccessLevel
import repositories.PostgresProfile.api._

object CompanyAccessColumnType {
  implicit val AccessLevelColumnType = MappedColumnType.base[AccessLevel, String](_.value, AccessLevel.fromValue)
}
