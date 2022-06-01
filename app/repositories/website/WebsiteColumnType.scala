package repositories.website

import models.website.WebsiteId
import models.website.WebsiteKind
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import java.util.UUID

object WebsiteColumnType {
  implicit val WebsiteKindColumnType = MappedColumnType.base[WebsiteKind, String](_.value, WebsiteKind.fromValue(_))

  implicit val WebsiteIdColumnType: JdbcType[WebsiteId] with BaseTypedType[WebsiteId] =
    MappedColumnType.base[WebsiteId, UUID](
      _.value,
      WebsiteId(_)
    )

}
