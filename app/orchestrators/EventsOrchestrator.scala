package orchestrators
import cats.implicits.catsSyntaxOption
import controllers.error.AppError.CompanySiretNotFound
import io.scalaland.chimney.dsl._
import models.User
import models.UserRole
import models.event.Event
import models.event.EventUser
import models.event.EventWithUser
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import utils.Constants.ActionEvent.REPORT_ASSIGNED
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.ActionEvent.REPORT_PRO_ENGAGEMENT_HONOURED
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Constants.ActionEvent.REPORT_READING_BY_PRO
import utils.Constants.ActionEvent.REPORT_REOPENED_BY_ADMIN
import utils.Constants.ActionEvent.REPORT_REVIEW_ON_RESPONSE
import utils.Constants.EventType
import utils.SIRET

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait EventsOrchestratorInterface {

  def getReportsEvents(
      reportId: UUID,
      eventType: Option[String],
      user: User
  ): Future[List[EventWithUser]]

  def getCompanyEvents(
      siret: SIRET,
      eventType: Option[String],
      userRole: UserRole
  ): Future[List[EventWithUser]]
}

class EventsOrchestrator(
    visibleReportOrchestrator: VisibleReportOrchestrator,
    eventRepository: EventRepositoryInterface,
    companyRepository: CompanyRepositoryInterface
)(implicit
    val ec: ExecutionContext
) extends EventsOrchestratorInterface {

  private val logger = Logger(this.getClass)

  override def getReportsEvents(
      reportId: UUID,
      eventType: Option[String],
      user: User
  ): Future[List[EventWithUser]] =
    for {
      _ <- Future.unit
      _ <- visibleReportOrchestrator.checkReportIsVisible(reportId, user)
      filter = buildEventFilter(eventType)
      _      = logger.debug("Fetching events")
      events <- eventRepository.getEventsWithUsers(List(reportId), filter)
      _            = logger.debug(s" ${events.length} reports events found")
      reportEvents = filterAndTransformEvents(user.userRole, events)
    } yield reportEvents

  override def getCompanyEvents(
      siret: SIRET,
      eventType: Option[String],
      userRole: UserRole
  ): Future[List[EventWithUser]] =
    for {
      maybeCompany <- companyRepository.findBySiret(siret)
      _ = logger.debug("Checking if company exists")
      company <- maybeCompany.liftTo[Future](CompanySiretNotFound(siret))
      _      = logger.debug("Found company")
      filter = buildEventFilter(eventType)
      _      = logger.debug("Fetching events")
      events <- eventRepository.getCompanyEventsWithUsers(company.id, filter)
      _             = logger.debug(s" ${events.length} company events found")
      companyEvents = filterAndTransformEvents(userRole, events)
    } yield companyEvents

  private def filterAndTransformEvents(userRole: UserRole, events: List[(Event, Option[User])]): List[EventWithUser] =
    filterOnUserRole(userRole, events).map { case (event, maybeUser) =>
      val maybeEventUser = maybeUser
        // Do not return event user for non pro event if requesting user is a PRO user
        .filter(e => userRole != UserRole.Professionnel || e.userRole == UserRole.Professionnel)
        .map(
          _.into[EventUser]
            .withFieldComputed(_.role, _.userRole)
            .transform
        )
      EventWithUser(event, maybeEventUser)
    }

  private def filterOnUserRole(userRole: UserRole, events: List[(Event, Option[User])]): List[(Event, Option[User])] =
    events.filter { case (event, _) =>
      userRole match {
        case UserRole.Professionnel =>
          List(
            REPORT_READING_BY_PRO,
            REPORT_PRO_RESPONSE,
            REPORT_PRO_ENGAGEMENT_HONOURED,
            REPORT_REVIEW_ON_RESPONSE,
            REPORT_CLOSED_BY_NO_READING,
            REPORT_CLOSED_BY_NO_ACTION,
            REPORT_REOPENED_BY_ADMIN,
            REPORT_ASSIGNED
          ) contains event.action
        case _ => true
      }
    }

  private def buildEventFilter(eventType: Option[String]) =
    eventType match {
      case Some(et) => EventFilter(eventType = EventType.withNameOption(et))
      case None     => EventFilter()
    }

}
