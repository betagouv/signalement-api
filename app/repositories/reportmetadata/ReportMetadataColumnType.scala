package repositories.reportmetadata

import models.report.reportmetadata.Os
import repositories.PostgresProfile.api._

object ReportMetadataColumnType {

  implicit val OsColumnType =
    MappedColumnType.base[Os, String](
      _.entryName,
      Os.namesToValuesMap
    )

}
