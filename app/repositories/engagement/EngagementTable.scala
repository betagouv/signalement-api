package repositories.engagement

import models.engagement.Engagement
import models.engagement.EngagementId
import repositories.PostgresProfile.api._
import repositories.TypedDatabaseTable
import slick.lifted.ProvenShape

import java.time.OffsetDateTime
import java.util.UUID

class EngagementTable(tag: Tag) extends TypedDatabaseTable[Engagement, EngagementId](tag, "engagements") {

  def reportId          = column[UUID]("report_id")
  def promiseEventId    = column[UUID]("promise_event_id")
  def resolutionEventId = column[Option[UUID]]("resolution_event_id")
  def expirationDate    = column[OffsetDateTime]("expiration_date")

  override def * : ProvenShape[Engagement] = (
    id,
    reportId,
    promiseEventId,
    resolutionEventId,
    expirationDate
  ) <> ((Engagement.apply _).tupled, Engagement.unapply)
}

object EngagementTable {
  val table = TableQuery[EngagementTable]
}
