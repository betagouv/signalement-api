package models.access

import io.scalaland.chimney.dsl.TransformerOps
import models.AccessLevel
import models.User
import play.api.libs.json.Json
import play.api.libs.json.Writes
import utils.EmailAddress

import java.util.UUID

case class UserWithAccessLevel(userId: UUID, firstName: String, lastName: String, email: EmailAddress, level: String)

object UserWithAccessLevel {

  implicit val UserWithAccessLevelWrites: Writes[UserWithAccessLevel] = Json.writes[UserWithAccessLevel]

  def toApi(userAccessLevelTuple: (User, AccessLevel)): UserWithAccessLevel = {
    val (user, accessLevel) = userAccessLevelTuple
    user
      .into[UserWithAccessLevel]
      .withFieldConst(_.userId, user.id)
      .withFieldConst(_.level, accessLevel.value)
      .transform
  }
}
