package repositories.report

import models.report.Gender
import models.report.ReportTag
import models.report.SocialNetworkSlug
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object ReportColumnType {

  implicit val ReportTagListColumnType: JdbcType[List[ReportTag]] with BaseTypedType[List[ReportTag]] =
    MappedColumnType.base[List[ReportTag], List[String]](
      _.map(_.entryName),
      _.map(ReportTag.namesToValuesMap)
    )

  implicit val ReportTagColumnType: JdbcType[ReportTag] with BaseTypedType[ReportTag] =
    MappedColumnType.base[ReportTag, String](
      _.entryName,
      ReportTag.namesToValuesMap
    )

  implicit val GenderColumnType: JdbcType[Gender] with BaseTypedType[Gender] =
    MappedColumnType.base[Gender, String](
      _.entryName,
      Gender.namesToValuesMap
    )

  implicit val SocialNetworkColumnType: JdbcType[SocialNetworkSlug] with BaseTypedType[SocialNetworkSlug] =
    MappedColumnType.base[SocialNetworkSlug, String](
      _.entryName,
      SocialNetworkSlug.namesToValuesMap
    )

}
