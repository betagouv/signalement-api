package models.access

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.User
import models.UserRole.Admin
import models.access.AccessesMassManagement.MassManagementOperation.Remove
import models.company.AccessLevel
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.token.ProAccessToken
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.libs.json.OWrites
import play.api.libs.json.Reads

import java.util.UUID

object AccessesMassManagement {
  case class MassManagementUsers(
      users: List[User],
      invitedByEmail: List[ProAccessToken]
  )
  object MassManagementUsers {
    implicit val writes: OWrites[MassManagementUsers] = Json.writes[MassManagementUsers]
  }
  sealed trait MassManagementOperation extends EnumEntry
  sealed trait MassManagementOperationSetAs extends MassManagementOperation {
    def desiredLevel: AccessLevel
  }
  object MassManagementOperation extends PlayEnum[MassManagementOperation] {
    final case object Remove    extends MassManagementOperation
    final case object SetMember extends MassManagementOperationSetAs { override def desiredLevel = MEMBER }
    final case object SetAdmin  extends MassManagementOperationSetAs { override def desiredLevel = ADMIN  }
    override def values: IndexedSeq[MassManagementOperation] = findValues

  }

  case class MassManagementInputs(
      operation: MassManagementOperation,
      companiesIds: List[UUID],
      users: MassManagementUsersInput
  )

  object MassManagementInputs {

    implicit val reads: Reads[MassManagementInputs] = Json
      .reads[MassManagementInputs]
      .filterNot(JsonValidationError("The emailsToInvite field should be empty when paired with the Remove operation"))(
        inputs => inputs.operation == Remove && inputs.users.emailsToInvite.nonEmpty
      )
      .filterNot(JsonValidationError("companiesIds should not be empty"))(_.companiesIds.isEmpty)
      .filterNot(JsonValidationError("should target at least one user"))(_.users.count == 0)
  }

  case class MassManagementUsersInput(
      usersIds: List[UUID],
      alreadyInvitedTokenIds: List[UUID],
      emailsToInvite: List[String]
  ) {
    def count = (usersIds ++ alreadyInvitedTokenIds ++ emailsToInvite).length
  }

  object MassManagementUsersInput {
    implicit val reads: Reads[MassManagementUsersInput] = Json.reads[MassManagementUsersInput]
  }

}
