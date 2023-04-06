package repositories.website

import models.investigation.InvestigationStatus

import models.website.WebsiteId
import models.website.IdentificationStatus
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import java.util.UUID

object WebsiteColumnType {

  implicit val IdentificationStatusListColumnType =
    MappedColumnType.base[List[IdentificationStatus], List[String]](
      _.map(_.entryName),
      _.map(IdentificationStatus.namesToValuesMap)
    )

  implicit val IdentificationStatusColumnType =
    MappedColumnType.base[IdentificationStatus, String](_.entryName, IdentificationStatus.withName)

  implicit val InvestigationColumnType =
    MappedColumnType.base[InvestigationStatus, String](_.entryName, InvestigationStatus.withName)

  implicit val WebsiteIdColumnType: JdbcType[WebsiteId] with BaseTypedType[WebsiteId] =
    MappedColumnType.base[WebsiteId, UUID](
      _.value,
      WebsiteId(_)
    )

}
