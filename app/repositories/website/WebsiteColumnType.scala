package repositories.website

import models.website.WebsiteKind
import repositories.PostgresProfile.api._

object WebsiteColumnType {
  implicit val WebsiteKindColumnType = MappedColumnType.base[WebsiteKind, String](_.value, WebsiteKind.fromValue(_))
}
