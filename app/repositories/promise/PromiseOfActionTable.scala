package repositories.promise

import models.promise.PromiseOfAction
import models.promise.PromiseOfActionId
import repositories.PostgresProfile.api._
import repositories.TypedDatabaseTable
import slick.lifted.ProvenShape

import java.util.UUID

class PromiseOfActionTable(tag: Tag)
    extends TypedDatabaseTable[PromiseOfAction, PromiseOfActionId](tag, "promises_of_action") {

  def reportId          = column[UUID]("report_id")
  def promiseEventId    = column[UUID]("promise_event_id")
  def resolutionEventId = column[Option[UUID]]("resolution_event_id")

  override def * : ProvenShape[PromiseOfAction] = (
    id,
    reportId,
    promiseEventId,
    resolutionEventId
  ) <> ((PromiseOfAction.apply _).tupled, PromiseOfAction.unapply)
}

object PromiseOfActionTable {
  val table = TableQuery[PromiseOfActionTable]
}
