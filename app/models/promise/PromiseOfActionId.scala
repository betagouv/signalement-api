package models.promise

import play.api.libs.json.Format
import play.api.libs.json.Json
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import repositories.PostgresProfile.api._

import java.util.UUID

case class PromiseOfActionId(value: UUID) extends AnyVal

object PromiseOfActionId {
  implicit val promiseOfActionIdFormat: Format[PromiseOfActionId] = Json.valueFormat[PromiseOfActionId]

  implicit val PromiseOfActionIdColumnType: JdbcType[PromiseOfActionId] with BaseTypedType[PromiseOfActionId] =
    MappedColumnType.base[PromiseOfActionId, UUID](
      _.value,
      PromiseOfActionId(_)
    )
}
