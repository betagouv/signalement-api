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
import utils.Constants.ReportStatus.{ReportStatusValue, SIGNALEMENT_TRANSMIS, TRAITEMENT_EN_COURS}
import utils.Constants.{ActionEvent, ReportStatus}
import utils.{AppSpec, EmailAddress, Fixtures}

import scala.concurrent.Await
import scala.concurrent.duration._

class RemindTransmittedReportOutOfTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is = {
    val event = transmittedEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(1)))
    s2"""
         Given a pro with email                                                       ${step(setupUser(proUser))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given an event "ENVOI_SIGNALEMENT" created more than 7 days                  ${step(setupEvent(event))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(transmittedReport.id, ActionEvent.RELANCE)}
         And the report is not updated                                                ${reportStatusMustNotHaveBeenUpdated(transmittedReport)}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(proUser.email,"Nouveau signalement", views.html.mails.professional.reportReminder(transmittedReport, OffsetDateTime.now.plusDays(14)).toString)}
    """
  }
}

class DontRemindTransmittedReportOnTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is = {
    val event = transmittedEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).plusDays(1)))
    s2"""
         Given a pro with email                                                       ${step(setupUser(proUser))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given an event "CONTACT_EMAIL" created less than 7 days                      ${step(setupEvent(event))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(transmittedReport.id, List(event))}
         And the report is not updated                                                ${reportStatusMustNotHaveBeenUpdated(transmittedReport)}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent}
    """
  }
}

class RemindTwiceTransmittedReportOutOfTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is = {
    val event = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(1)))
    s2"""
         Given a pro with email                                                       ${step(setupUser(proUser))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given a previous remind made more than 7 days                                ${step(setupEvent(event))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(transmittedReport.id, ActionEvent.RELANCE)}
         And the report is not updated                                                ${reportStatusMustNotHaveBeenUpdated(transmittedReport)}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(proUser.email,"Nouveau signalement", views.html.mails.professional.reportReminder(transmittedReport, OffsetDateTime.now.plusDays(7)).toString)}
    """
  }
}

class DontRemindTwiceTransmittedReportOnTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is = {
    val event = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).plusDays(1)))
    s2"""
         Given a pro with email                                                       ${step(setupUser(proUser))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given a previous remind made more than 7 days                                ${step(setupEvent(event))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(transmittedReport.id, List(reminderEvent))}
         And the report is not updated                                                ${reportStatusMustNotHaveBeenUpdated(transmittedReport)}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent}
    """
  }
}

class CloseTransmittedReportOutOfTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is = {
    val event1 = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(8)))
    val event2 = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(1)), id = Some(UUID.randomUUID))
    s2"""
         Given a pro with email                                                       ${step(setupUser(proUser))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given twice previous remind made more than 7 days                            ${step(setupEvent(event1))}
                                                                                      ${step(setupEvent(event2))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf))}
         Then an event "CONSULTE_IGNORE" is created                                   ${eventMustHaveBeenCreatedWithAction(transmittedReport.id, ActionEvent.CONSULTE_IGNORE)}
         And the report status is updated to "SIGNALEMENT_NON_CONSULTE"               ${reportMustHaveBeenUpdatedWithStatus(transmittedReport.id, ReportStatus.SIGNALEMENT_CONSULTE_IGNORE)}
         And a mail is sent to the consumer                                           ${mailMustHaveBeenSent(transmittedReport.email,"L'entreprise n'a pas répondu au signalement", views.html.mails.consumer.reportClosedByNoAction(transmittedReport).toString, mailerService.attachmentSeqForWorkflowStepN(4))}
   """
  }
}

class DontCloseTransmittedReportOnTime(implicit ee: ExecutionEnv) extends TransmittedReportReminderTaskSpec {
  override def is = {
    val event1 = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(8)))
    val event2 = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).plusDays(1)), id = Some(UUID.randomUUID))
    s2"""
         Given a pro with email                                                       ${step(setupUser(proUser))}
         Given a report with status "SIGNALEMENT_TRANSMIS"                            ${step(setupReport(transmittedReport))}
         Given a first remind made more than 7 days                                   ${step(setupEvent(event1))}
         Given a second remind made less than 7 days                                  ${step(setupEvent(event2))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf))}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(transmittedReport.id, List(event1, event2))}
         And the report is not updated                                                ${reportStatusMustNotHaveBeenUpdated(transmittedReport)}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent}
   """
  }
}

abstract class TransmittedReportReminderTaskSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with Mockito with FutureMatchers {

  implicit val ec = ee.executionContext
  val mailReminderDelay = java.time.Period.parse(app.configuration.get[String]("play.reports.mailReminderDelay"))

  val runningDateTime = OffsetDateTime.now

  val proUser = Fixtures.genProUser.sample.get

  val company = Fixtures.genCompany.sample.get
  val transmittedReport = Fixtures.genReportForCompany(company).sample.get.copy(
    status = SIGNALEMENT_TRANSMIS
  )

  val reminderEvent = Fixtures.genEventForReport(transmittedReport.id, PRO, RELANCE).sample.get
  val transmittedEvent = Fixtures.genEventForReport(transmittedReport.id, PRO, ENVOI_SIGNALEMENT).sample.get

  def mailMustHaveBeenSent(recipient: EmailAddress, subject: String, bodyHtml: String, attachments: Seq[Attachment] = Nil) = {
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        Seq(recipient),
        Nil,
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
    eventRepository.getEvents(None, Some(reportUUID), EventFilter(action = Some(action))).map(_.head) must eventActionMatcher(action).await
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def eventMustNotHaveBeenCreated(reportUUID: UUID, existingEvents: List[Event]) = {
    eventRepository.getEvents(None, Some(reportUUID), EventFilter()).map(_.length) must beEqualTo(existingEvents.length).await
  }

  def reportMustHaveBeenUpdatedWithStatus(reportUUID: UUID, status: ReportStatusValue) = {
    reportRepository.getReport(reportUUID) must reportStatusMatcher(status).await
  }

  def reportStatusMatcher(status: ReportStatusValue): org.specs2.matcher.Matcher[Option[Report]] = { report: Option[Report] =>
    (report.map(report => status == report.status).getOrElse(false), s"status doesn't match ${status}")
  }

  def reportStatusMustNotHaveBeenUpdated(report: Report) = {
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
        company <- companyRepository.getOrCreate(company.siret, company)
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
