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
      invitedEmails: List[String]
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
      // existing users
      // -> we can remove/add access to some companies
      usersIds: List[UUID],
      // people that have been invited to join one company but have not created their account yet (i.e. access_token COMPANY_JOIN)
      // -> we can remove their invitation or expand it to more companies
      alreadyInvitedEmails: List[String],
      // some new emails, manually typed by the requesting user, that we are gonna invite to the companies
      // Note that usually it will be emails that are brand new to SignalConso, that don't have accounts
      // but it is possible that they already have a user account, or an invitation,
      // but it wasn't on any company that our requesting user can see.
      emailsToInvite: List[String]
  ) {
    def count = (usersIds ++ alreadyInvitedEmails ++ emailsToInvite).length
  }

  object MassManagementUsersInput {
    implicit val reads: Reads[MassManagementUsersInput] = Json.reads[MassManagementUsersInput]
  }

}
