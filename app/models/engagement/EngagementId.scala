package models.engagement

import play.api.libs.json.Format
import play.api.libs.json.Json
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import repositories.PostgresProfile.api._

import java.util.UUID

case class EngagementId(value: UUID) extends AnyVal

object EngagementId {
  implicit val engagementIdFormat: Format[EngagementId] = Json.valueFormat[EngagementId]

  implicit val engagementIdColumnType: JdbcType[EngagementId] with BaseTypedType[EngagementId] =
    MappedColumnType.base[EngagementId, UUID](
      _.value,
      EngagementId(_)
    )
}
