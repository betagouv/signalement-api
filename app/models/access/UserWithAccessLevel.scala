package models.access

import io.scalaland.chimney.dsl._
import models.User
import models.company.AccessLevel
import play.api.libs.json.Json
import play.api.libs.json.Writes
import utils.EmailAddress

import java.util.UUID

case class UserWithAccessLevel(
    userId: UUID,
    firstName: String,
    lastName: String,
    email: EmailAddress,
    level: String,
    editable: Boolean,
    isHeadOffice: Boolean
)

object UserWithAccessLevel {

  implicit val UserWithAccessLevelWrites: Writes[UserWithAccessLevel] = Json.writes[UserWithAccessLevel]

  def toApi(user: User, accessLevel: AccessLevel, editable: Boolean, isHeadOffice: Boolean): UserWithAccessLevel =
    user
      .into[UserWithAccessLevel]
      .withFieldConst(_.userId, user.id)
      .withFieldConst(_.editable, editable)
      .withFieldConst(_.level, accessLevel.value)
      .withFieldConst(_.isHeadOffice, isHeadOffice)
      .transform
}

case class UserWithAccessLevelAndNbResponse(
    userId: UUID,
    firstName: String,
    lastName: String,
    email: EmailAddress,
    level: String,
    editable: Boolean,
    isHeadOffice: Boolean,
    nbResponses: Int
)

object UserWithAccessLevelAndNbResponse {

  implicit val UserWithAccessLevelAndNbResponsesWrites: Writes[UserWithAccessLevelAndNbResponse] =
    Json.writes[UserWithAccessLevelAndNbResponse]

  def build(access: UserWithAccessLevel, nbResponses: Int): UserWithAccessLevelAndNbResponse =
    access
      .into[UserWithAccessLevelAndNbResponse]
      .withFieldConst(_.nbResponses, nbResponses)
      .transform
}
