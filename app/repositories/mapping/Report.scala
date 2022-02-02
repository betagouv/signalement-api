package repositories.mapping

import models.report.ReportTag
import repositories.PostgresProfile.api._

object Report {

  implicit val ReportTagListColumnType =
    MappedColumnType.base[List[ReportTag], List[String]](
      _.map(_.entryName),
      _.map(ReportTag.namesToValuesMap)
    )

  implicit val ReportTagColumnType =
    MappedColumnType.base[ReportTag, String](
      _.entryName,
      ReportTag.namesToValuesMap
    )

}
