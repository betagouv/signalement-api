package services

import models.User
import models.access.AccessesMassManagement.MassManagementOperation
import models.company.Company
import models.event.Event
import play.api.libs.json.Json
import utils.Constants
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

object EventsBuilder {

  def userAccessRemovedEvent(companyId: UUID, affectedUser: User, requestedBy: User): Event =
    Event(
      UUID.randomUUID(),
      None,
      Some(companyId),
      Some(requestedBy.id),
      OffsetDateTime.now(),
      Constants.EventType.fromUserRole(requestedBy.userRole),
      Constants.ActionEvent.USER_ACCESS_REMOVED,
      Json.obj("userId" -> affectedUser.id, "email" -> affectedUser.email)
    )

  def accessesMassManagementEvent(
      operation: MassManagementOperation,
      companies: List[Company],
      users: List[User],
      emails: List[EmailAddress],
      requestedBy: User
  ) =
    Event(
      id = UUID.randomUUID(),
      reportId = None,
      companyId = None,
      userId = Some(requestedBy.id),
      creationDate = OffsetDateTime.now(),
      eventType = Constants.EventType.fromUserRole(requestedBy.userRole),
      action = Constants.ActionEvent.ACCESSES_MASS_MANAGED,
      details = Json.obj(
        "operation" -> operation,
        "companies" -> companies.map(_.id),
        "users"     -> users.map(_.id),
        "emails"    -> emails.map(_.value)
      )
    )

}
