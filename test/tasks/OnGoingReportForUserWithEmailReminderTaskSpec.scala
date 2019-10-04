package tasks

import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

import models.UserRoles.Pro
import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import play.api.libs.mailer.{Attachment, AttachmentFile}
import repositories._
import services.MailerService
import utils.AppSpec
import utils.Constants.ActionEvent.{ActionEventValue, CONTACT_COURRIER, CONTACT_EMAIL, RELANCE}
import utils.Constants.EventType.PRO
import utils.Constants.StatusPro.{StatusProValue, TRAITEMENT_EN_COURS}
import utils.Constants.{ActionEvent, StatusPro}

import scala.concurrent.Await
import scala.concurrent.duration._

class RemindOngoingReportOutOfTimeForUserWithEmail(implicit ee: ExecutionEnv) extends OnGoingReportForUserWithEmailReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(onGoingReport))}
         Given an event "CONTACT_EMAIL" created more than 7 days                      ${step(setupEvent(outOfTimeContactByMailEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(reportUUID, ActionEvent.RELANCE)}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(onGoingReport)}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(userWithEmail.email.get,"Nouveau signalement", views.html.mails.professional.reportNotification(onGoingReport).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

class DontRemindOngoingReportOnTimeForUserWithEmail(implicit ee: ExecutionEnv) extends OnGoingReportForUserWithEmailReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(onGoingReport))}
         Given an event "CONTACT_EMAIL" created less than 7 days                      ${step(setupEvent(onTimeContactByMailEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(reportUUID, List(onTimeContactByMailEvent))}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(onGoingReport)}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent}
    """
}

class RemindTwiceOngoingReportOutOfTimeForUserWithEmail(implicit ee: ExecutionEnv) extends OnGoingReportForUserWithEmailReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(onGoingReport))}
         Given a previous remind made more than 21 days                               ${step(setupEvent(outOfTimeReminderEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(reportUUID, ActionEvent.RELANCE)}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(onGoingReport)}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(userWithEmail.email.get,"Nouveau signalement", views.html.mails.professional.reportNotification(onGoingReport).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

abstract class OnGoingReportForUserWithEmailReminderTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with Mockito with FutureMatchers {

  implicit val ec = ee.executionContext

  val runningDateTime = LocalDate.of(2019, 9, 26).atStartOfDay()

  val userWithEmail = User(UUID.randomUUID(), "22222222222222", "", None, Some("email"), None, Some("test"), Pro)

  val reportUUID = UUID.randomUUID()

  val onGoingReport = Report(Some(reportUUID), "test", List.empty, List("détails test"), "company1", "addresse" + UUID.randomUUID().toString, None,
    Some(userWithEmail.login),
    Some(OffsetDateTime.of(2019, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC)), "r1", "nom 1", "email 1", true, List.empty,
    Some(TRAITEMENT_EN_COURS), None)
  val outOfTimeContactByMailEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    CONTACT_EMAIL, None, Some("test"))
  val onTimeContactByMailEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 20, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    CONTACT_EMAIL, None, Some("test"))
  val outOfTimeReminderEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    RELANCE, None, Some("test"))
  val onTimeReminderEvent = Event(Some(UUID.randomUUID() ), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 20, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    RELANCE, None, Some("test"))




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

  def reportMustHaveBeenUpdatedWithStatus(reportUUID: UUID, status: StatusProValue) = {
    reportRepository.getReport(reportUUID) must reportStatusProMatcher(Some(status)).await
  }

  def reportStatusProMatcher(status: Option[StatusProValue]): org.specs2.matcher.Matcher[Option[Report]] = { report: Option[Report] =>
    (report.map(report => status == report.statusPro).getOrElse(false), s"status doesn't match ${status}")
  }

  def reporStatustMustNotHaveBeenUpdated(report: Report) = {
    reportRepository.getReport(report.id.get).map(_.get.statusPro) must beEqualTo(report.statusPro).await
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
