package repositories.accesstoken

import models.token.TokenKind
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object AccessTokenColumnType {

  implicit val TokenKindColumnType: JdbcType[TokenKind] with BaseTypedType[TokenKind] =
    MappedColumnType.base[TokenKind, String](_.entryName, TokenKind.withName(_))

}
