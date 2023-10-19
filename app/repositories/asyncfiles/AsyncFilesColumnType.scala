package repositories.asyncfiles

import models.AsyncFileKind
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object AsyncFilesColumnType {

  implicit val AsyncFileKindColumnType: JdbcType[AsyncFileKind] with BaseTypedType[AsyncFileKind] =
    MappedColumnType.base[AsyncFileKind, String](
      _.entryName,
      AsyncFileKind.namesToValuesMap
    )

}
