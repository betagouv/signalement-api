package repositories.event

import cats.data.NonEmptyList
import models.User
import models.event.Event
import models.report.EventWithUser
import models.report.Report
import repositories.CRUDRepositoryInterface
import utils.Constants.ActionEvent
import utils.Constants.ActionEvent.ActionEventValue

import java.sql.Timestamp
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

trait EventRepositoryInterface extends CRUDRepositoryInterface[Event] {

  def deleteByReportId(uuidReport: UUID): Future[Int]

  def deleteEngagement(uuidReport: UUID): Future[Int]

  def countEvents(filter: EventFilter): Future[Int]
  def getEvents(reportId: UUID, filter: EventFilter = EventFilter()): Future[List[Event]]

  def getEventsWithUsers(reportsIds: List[UUID], filter: EventFilter): Future[List[(Event, Option[User])]]

  def getEventsWithUsersMap(reportsIds: List[UUID], filter: EventFilter): Future[Map[UUID, List[EventWithUser]]]

  def getCompanyEventsWithUsers(companyId: UUID, filter: EventFilter): Future[List[(Event, Option[User])]]

  def getReportResponseReviews(companyId: Option[UUID]): Future[Seq[Event]]

  def fetchEventsOfReports(reports: List[Report]): Future[Map[UUID, List[Event]]]

  def fetchEvents(companyIds: List[UUID]): Future[Map[UUID, List[Event]]]

  def fetchEventFromActionEvents(
      companyId: UUID,
      action: ActionEventValue
  ): Future[List[Event]]

  def getAvgTimeUntilEvent(
      action: ActionEvent.ActionEventValue,
      companyId: Option[UUID] = None,
      onlyProShareable: Boolean = false
  ): Future[Option[Duration]]

  def getReportCountHavingEvent(action: ActionEvent.ActionEventValue, companyId: Option[UUID] = None): Future[Int]

  def getProReportStat(
      ticks: Int,
      startingDate: OffsetDateTime,
      actions: NonEmptyList[ActionEvent.ActionEventValue]
  ): Future[Vector[(Timestamp, Int)]]

  def fetchEventCountFromActionEvents(
      companyId: UUID,
      action: ActionEvent.ActionEventValue
  ): Future[Int]
}
