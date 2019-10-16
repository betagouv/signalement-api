package tasks

import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

import models.UserRoles.Pro
import models._
import models.Event._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import play.api.libs.mailer.Attachment
import repositories._
import services.MailerService
import utils.AppSpec
import utils.Constants.{ActionEvent, ReportStatus}
import utils.Constants.ActionEvent.{ActionEventValue, CONTACT_COURRIER, RELANCE}
import utils.Constants.EventType.PRO
import utils.Constants.ReportStatus.{ReportStatusValue, TRAITEMENT_EN_COURS}

import scala.concurrent.Await
import scala.concurrent.duration._

class RemindOngoingReportOutOfTimeForUserWithoutEmail(implicit ee: ExecutionEnv) extends OnGoingReportForUserWithoutEmailReminderTaskSpec {
  override def is =
    s2"""
         Given a pro without email                                                    ${step(setupUser(userWithoutEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(onGoingReport))}
         Given an event "CONTACT_COURRIER" created more than 21 days                  ${step(setupEvent(outOfTimeContactByPostEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(reportUUID, ActionEvent.RELANCE)}
         And the report status is updated to "A_TRAITER"                              ${reportMustHaveBeenUpdatedWithStatus(reportUUID, ReportStatus.A_TRAITER)}
    """
}

class DontRemindOngoingReportOnTimeForUserWithoutEmail(implicit ee: ExecutionEnv) extends OnGoingReportForUserWithoutEmailReminderTaskSpec {
  override def is =
    s2"""
         Given a pro without email                                                    ${step(setupUser(userWithoutEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(onGoingReport))}
         Given an event "CONTACT_COURRIER" created less than 21 days                  ${step(setupEvent(onTimeContactByPostEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(reportUUID, List(onTimeContactByPostEvent))}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(onGoingReport)}
    """
}

class CloseOngoingReportOutOfTimeForUserWithoutEmail(implicit ee: ExecutionEnv) extends OnGoingReportForUserWithoutEmailReminderTaskSpec {
  override def is =
    s2"""
         Given a pro without email                                                    ${step(setupUser(userWithoutEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(onGoingReport))}
         Given a previous remind made more than 21 days                               ${step(setupEvent(outOfTimeReminderEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then an event "NON_CONSULTE" is created                                      ${eventMustHaveBeenCreatedWithAction(reportUUID, ActionEvent.NON_CONSULTE)}
         And the report status is updated to "SIGNALEMENT_NON_CONSULTE"               ${reportMustHaveBeenUpdatedWithStatus(reportUUID, ReportStatus.SIGNALEMENT_NON_CONSULTE)}
    """
}

class DontCloseOngoingReportOnTimeForUserWithoutEmail(implicit ee: ExecutionEnv) extends OnGoingReportForUserWithoutEmailReminderTaskSpec {
  override def is =
    s2"""
         Given a pro without email                                                    ${step(setupUser(userWithoutEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(onGoingReport))}
         Given a previous remind made less than 21 days                               ${step(setupEvent(onTimeContactByPostEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(reportUUID, List(onTimeContactByPostEvent))}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(onGoingReport)}
    """
}


abstract class OnGoingReportForUserWithoutEmailReminderTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with Mockito with FutureMatchers {

  implicit val ec = ee.executionContext

  val runningDateTime = LocalDate.of(2019, 9, 26).atStartOfDay()

  val userWithoutEmail = User(UUID.randomUUID(), "11111111111111", "", Some("123123"), None, None, Some("test"), Pro)

  val reportUUID = UUID.randomUUID()

  val onGoingReport = Report(Some(reportUUID), "test", List.empty, List("détails test"), "company1", "addresse" + UUID.randomUUID().toString, None,
    Some(userWithoutEmail.login),
    Some(OffsetDateTime.of(2019, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC)), "r1", "nom 1", "email 1", true, List.empty,
    Some(TRAITEMENT_EN_COURS))
  val outOfTimeContactByPostEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithoutEmail.id),
    Some(OffsetDateTime.of(2019, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    CONTACT_COURRIER, None, Some(stringToDetailsJsValue("test")))
  val onTimeContactByPostEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithoutEmail.id),
    Some(OffsetDateTime.of(2019, 9, 6, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    CONTACT_COURRIER, None, Some(stringToDetailsJsValue("test")))
  val outOfTimeReminderEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithoutEmail.id),
    Some(OffsetDateTime.of(2019, 9, 4, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    RELANCE, None, Some(stringToDetailsJsValue("test")))
  val onTimeReminderEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithoutEmail.id),
    Some(OffsetDateTime.of(2019, 9, 8, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    RELANCE, None, Some(stringToDetailsJsValue("test")))

  def mailMustHaveBeenSent(recipient: String, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(app.injector.instanceOf[MailerService])
      .sendEmail(
        app.configuration.get[String]("play.mail.from"),
        recipient
      )(
        subject,
        bodyHtml,
        attachments
      )
  }

  def mailMustNotHaveBeenSent() = {
    there was no(app.injector.instanceOf[MailerService]).sendEmail(anyString, anyString)(anyString, anyString, any)
  }

  def eventMustHaveBeenCreatedWithAction(reportUUID: UUID, action: ActionEventValue) = {
    eventRepository.getEvents(reportUUID, EventFilter(action = Some(action))).map(_.head) must eventActionMatcher(action).await
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def eventMustNotHaveBeenCreated(reportUUID: UUID, existingEvents: List[Event]) = {
    eventRepository.getEvents(reportUUID, EventFilter()).map(_.length) must beEqualTo(existingEvents.length).await
  }

  def reportMustHaveBeenUpdatedWithStatus(reportUUID: UUID, status: ReportStatusValue) = {
    reportRepository.getReport(reportUUID) must reportStatusMatcher(Some(status)).await
  }

  def reportStatusMatcher(status: Option[ReportStatusValue]): org.specs2.matcher.Matcher[Option[Report]] = { report: Option[Report] =>
    (report.map(report => status == report.status).getOrElse(false), s"status doesn't match ${status}")
  }

  def reporStatustMustNotHaveBeenUpdated(report: Report) = {
    reportRepository.getReport(report.id.get).map(_.get.status) must beEqualTo(report.status).await
  }

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val eventRepository = injector.instanceOf[EventRepository]
  lazy val reminderTask = injector.instanceOf[ReminderTask]

  def setupUser(user: User) = {
    Await.result(userRepository.create(user), Duration.Inf)
  }
  def setupReport(report: Report) = {
    Await.result(reportRepository.create(report), Duration.Inf)
  }
  def setupEvent(event: Event) = {
    Await.result(eventRepository.createEvent(event), Duration.Inf)
  }
  override def setupData() {
  }
}
