package models.access

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.User
import models.token.ProAccessToken
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Reads

object AccessesMassManagement {
  case class MassManagementUsers(
      users: List[User],
      invitedByEmail: List[ProAccessToken]
  )
  object MassManagementUsers {
    implicit val writes: OWrites[MassManagementUsers] = Json.writes[MassManagementUsers]
  }
  sealed trait MassManagementOperation extends EnumEntry
  object MassManagementOperation extends PlayEnum[MassManagementOperation] {
    final case object Remove    extends MassManagementOperation
    final case object SetMember extends MassManagementOperation
    final case object SetAdmin  extends MassManagementOperation
    override def values: IndexedSeq[MassManagementOperation] = findValues
  }

  case class MassManagementInputs(
      operation: MassManagementOperation,
      companiesIds: List[String],
      users: MassManagementUsersInput
  )

  object MassManagementInputs {

    implicit val reads: Reads[MassManagementInputs] = Json.reads[MassManagementInputs]
  }

  case class MassManagementUsersInput(
      usersIds: List[String],
      alreadyInvitedTokenIds: List[String],
      emailToInvite: List[String]
  )

  object MassManagementUsersInput {
    implicit val reads: Reads[MassManagementUsersInput] = Json.reads[MassManagementUsersInput]
  }

}
