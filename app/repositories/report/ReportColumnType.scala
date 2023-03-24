package repositories.report

import models.report.Gender
import models.report.ReportTag
import models.report.SocialNetworkSlug
import repositories.PostgresProfile.api._

object ReportColumnType {

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

  implicit val GenderColumnType =
    MappedColumnType.base[Gender, String](
      _.entryName,
      Gender.namesToValuesMap
    )

  implicit val SocialNetworkColumnType =
    MappedColumnType.base[SocialNetworkSlug, String](
      _.entryName,
      SocialNetworkSlug.namesToValuesMap
    )

}
