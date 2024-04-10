package models.promise

import java.util.UUID

case class PromiseOfAction(id: PromiseOfActionId, reportId: UUID, promiseEventId: UUID, resolutionEventId: Option[UUID])
