package repositories.reportmetadata

import models.report.reportmetadata.Os
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object ReportMetadataColumnType {

  implicit val OsColumnType: JdbcType[Os] with BaseTypedType[Os] =
    MappedColumnType.base[Os, String](
      _.entryName,
      Os.namesToValuesMap
    )

}
