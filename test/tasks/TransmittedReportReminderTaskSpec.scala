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
import play.api.libs.mailer.{Attachment, AttachmentFile}
import repositories._
import services.MailerService
import utils.AppSpec
import utils.Constants.ActionEvent.{ActionEventValue, CONTACT_EMAIL, ENVOI_SIGNALEMENT, RELANCE}
import utils.Constants.EventType.PRO
import utils.Constants.ReportStatus.{SIGNALEMENT_TRANSMIS, ReportStatusValue, TRAITEMENT_EN_COURS}
import utils.Constants.{ActionEvent, ReportStatus}

import scala.concurrent.Await
import scala.concurrent.duration._

class RemindTransmittedReportOutOfTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given an event "ENVOI_SIGNALEMENT" created more than 7 days                  ${step(setupEvent(outOfTimeReportTransmittedEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(reportUUID, ActionEvent.RELANCE)}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(transmittedReport)}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(userWithEmail.email.get,"Nouveau signalement", views.html.mails.professional.reportNotification(transmittedReport).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

class DontRemindTransmittedReportOnTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given an event "CONTACT_EMAIL" created less than 7 days                      ${step(setupEvent(onTimeReportTransmittedEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(reportUUID, List(onTimeReportTransmittedEvent))}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(transmittedReport)}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent}
    """
}

class RemindTwiceTransmittedReportOutOfTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given a previous remind made more than 7 days                                ${step(setupEvent(outOfTimeReminderEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(reportUUID, ActionEvent.RELANCE)}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(transmittedReport)}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(userWithEmail.email.get,"Nouveau signalement", views.html.mails.professional.reportNotification(transmittedReport).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
    """
}

class DontRemindTwiceTransmittedReportOnTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given a previous remind made more than 7 days                                ${step(setupEvent(onTimeReminderEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(reportUUID, List(onTimeReminderEvent))}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(transmittedReport)}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent}
    """
}

class CloseTransmittedReportOutOfTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given twice previous remind made more than 7 days                            ${step(setupEvent(outOfTimeReminderEvent))}
                                                                                      ${step(setupEvent(outOfTimeReminderEvent.copy(id = Some(UUID.randomUUID))))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then an event "CONSULTE_IGNORE" is created                                   ${eventMustHaveBeenCreatedWithAction(reportUUID, ActionEvent.CONSULTE_IGNORE)}
         And the report status is updated to "SIGNALEMENT_NON_CONSULTE"               ${reportMustHaveBeenUpdatedWithStatus(reportUUID, ReportStatus.SIGNALEMENT_CONSULTE_IGNORE)}
         And a mail is sent to the consumer                                           ${mailMustHaveBeenSent(transmittedReport.email,"Le professionnel n’a pas répondu au signalement", views.html.mails.consumer.reportClosedByNoAction(transmittedReport).toString, Seq(AttachmentFile("logo-signal-conso.png", app.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))}
   """
}

class DontCloseTransmittedReportOnTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is =
    s2"""
         Given a pro with email                                                       ${step(setupUser(userWithEmail))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given a first remind made more than 7 days                                   ${step(setupEvent(outOfTimeReminderEvent))}
         Given a second remind made less than 7 days                                  ${step(setupEvent(onTimeReminderEvent))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(reportUUID, List(outOfTimeReminderEvent, onTimeReminderEvent))}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(transmittedReport)}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent}
   """
}

abstract class TransmittedReportReminderTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with Mockito with FutureMatchers {

  implicit val ec = ee.executionContext

  val runningDateTime = LocalDate.of(2019, 9, 26).atStartOfDay()

  val userWithEmail = User(UUID.randomUUID(), "22222222222222", "", None, Some("email"), None, Some("test"), Pro)

  val reportUUID = UUID.randomUUID()

  val transmittedReport = Report(Some(reportUUID), "test", List.empty, List("détails test"), "company1", "addresse" + UUID.randomUUID().toString, None,
    Some(userWithEmail.login),
    Some(OffsetDateTime.of(2019, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC)), "r1", "nom 1", "email 1", true, List.empty,
    Some(SIGNALEMENT_TRANSMIS))
  val outOfTimeReportTransmittedEvent = Event(Some(UUID.randomUUID()), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    ENVOI_SIGNALEMENT, stringToDetailsJsValue("test"))
  val onTimeReportTransmittedEvent = Event(Some(UUID.randomUUID()), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 20, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    ENVOI_SIGNALEMENT, stringToDetailsJsValue("test"))
  val outOfTimeReminderEvent = Event(Some(UUID.randomUUID()), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    RELANCE, stringToDetailsJsValue("test"))
  val onTimeReminderEvent = Event(Some(UUID.randomUUID()), Some(reportUUID),
    Some(userWithEmail.id),
    Some(OffsetDateTime.of(2019, 9, 20, 0, 0, 0, 0, ZoneOffset.UTC)), PRO,
    RELANCE, stringToDetailsJsValue("test"))




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
