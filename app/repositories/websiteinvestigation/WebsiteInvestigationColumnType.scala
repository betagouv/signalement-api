package repositories.websiteinvestigation

import models.investigation.DepartmentDivision
import models.investigation.InvestigationStatus
import models.investigation.Practice
import models.investigation.WebsiteInvestigationId
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import java.util.UUID

object WebsiteInvestigationColumnType {

  implicit val InvestigationColumnType =
    MappedColumnType.base[InvestigationStatus, String](_.entryName, InvestigationStatus.withName)

  implicit val PracticeTypeColumnType = MappedColumnType.base[Practice, String](_.entryName, Practice.withName)

  implicit val DepartmentDivisionColumnType =
    MappedColumnType.base[DepartmentDivision, String](_.entryName, DepartmentDivision.withName)

  implicit val WebsiteInvestigationIdColumnType
      : JdbcType[WebsiteInvestigationId] with BaseTypedType[WebsiteInvestigationId] =
    MappedColumnType.base[WebsiteInvestigationId, UUID](
      _.value,
      WebsiteInvestigationId(_)
    )

}
