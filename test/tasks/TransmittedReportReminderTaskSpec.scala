package tasks

import java.net.URI
import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

import models.Event._
import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import play.api.Configuration
import play.api.libs.mailer.Attachment
import repositories._
import services.MailerService
import utils.Constants.ActionEvent.{ActionEventValue, ENVOI_SIGNALEMENT, RELANCE}
import utils.Constants.EventType.PRO
import utils.Constants.ReportStatus.{ReportStatusValue, SIGNALEMENT_TRANSMIS}
import utils.Constants.{ActionEvent, ReportStatus}
import utils.{AppSpec, EmailAddress, Fixtures}

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
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(userWithEmail.email,"Nouveau signalement", views.html.mails.professional.reportReminder(transmittedReport, OffsetDateTime.now.plusDays(14)).toString)}
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
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(userWithEmail.email,"Nouveau signalement", views.html.mails.professional.reportReminder(transmittedReport, OffsetDateTime.now.plusDays(7)).toString)}
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
         And a mail is sent to the consumer                                           ${mailMustHaveBeenSent(transmittedReport.email,"L'entreprise n'a pas répondu au signalement", views.html.mails.consumer.reportClosedByNoAction(transmittedReport).toString, mailerService.attachmentSeqForWorkflowStepN(4))}
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

  val userWithEmail = Fixtures.genProUser.sample.get

  val companyData = Company(
    UUID.randomUUID(),
    Fixtures.genSiret.sample.get,
    OffsetDateTime.now,
    "Test entreprise",
    "10 rue des Champs",
    Some("75010"),
  )

  val reportUUID = UUID.randomUUID()

  val transmittedReport = Report(reportUUID, "test", List.empty, List("détails test"), Some(companyData.id), "company1", "addresse" + UUID.randomUUID().toString, None,
    Fixtures.genSiret.sample,
    OffsetDateTime.of(2019, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC), "r1", "nom 1", EmailAddress("email 1"), true, false,
    SIGNALEMENT_TRANSMIS)
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

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = null) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        Seq(recipient),
        null,
        subject,
        bodyHtml,
        attachments
      )
  }

  def mailMustNotHaveBeenSent() = {
    there was no(app.injector.instanceOf[MailerService])
      .sendEmail(
        EmailAddress(anyString),
        any[Seq[EmailAddress]],
        any[Seq[EmailAddress]],
        anyString,
        anyString,
        any
      )
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
    reportRepository.getReport(reportUUID) must reportStatusMatcher(status).await
  }

  def reportStatusMatcher(status: ReportStatusValue): org.specs2.matcher.Matcher[Option[Report]] = { report: Option[Report] =>
    (report.map(report => status == report.status).getOrElse(false), s"status doesn't match ${status}")
  }

  def reporStatustMustNotHaveBeenUpdated(report: Report) = {
    reportRepository.getReport(report.id).map(_.get.status) must beEqualTo(report.status).await
  }

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val eventRepository = injector.instanceOf[EventRepository]
  lazy val reminderTask = injector.instanceOf[ReminderTask]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val accessTokenRepository = app.injector.instanceOf[AccessTokenRepository]
  lazy val mailerService = app.injector.instanceOf[MailerService]

  implicit lazy val websiteUrl = app.injector.instanceOf[Configuration].get[URI]("play.website.url")
  implicit lazy val contactAddress = app.injector.instanceOf[Configuration].get[EmailAddress]("play.mail.contactAddress")

  def setupUser(user: User) = {
    Await.result(
      for {
        company <- companyRepository.getOrCreate(companyData.siret, companyData)
        admin   <- userRepository.create(user)
        _       <- companyRepository.setUserLevel(company, admin, AccessLevel.ADMIN)
      } yield Unit,
      Duration.Inf
    )
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
