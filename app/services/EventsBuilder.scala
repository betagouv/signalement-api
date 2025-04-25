package services

import models.User
import models.event.Event
import play.api.libs.json.Json
import utils.Constants

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

}
