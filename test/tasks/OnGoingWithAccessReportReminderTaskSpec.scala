package tasks

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import play.api.Configuration
import play.api.libs.mailer.Attachment
import repositories._
import services.MailerService
import utils.AppSpec
import utils.Constants.ActionEvent
import utils.Constants.ReportStatus
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.EventType.PRO
import utils.Constants.ReportStatus.ReportStatusValue
import utils.Constants.ReportStatus.TRAITEMENT_EN_COURS
import utils.EmailAddress
import utils.Fixtures
import scala.concurrent.Await
import scala.concurrent.duration._

class RemindOnceUnreadWithAccessReport(implicit ee: ExecutionEnv) extends OnGoingWithAccessReportReminderTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = runningDateTime.minus(mailReminderDelay).minusDays(1))
    s2"""
         Given a pro user with activated account                                      ${step(setupUser(proUser))}
         Given a report with status "TRAITEMENT_EN_COURS" created more than 7 days    ${step(setupReport(report))}
         When remind task run                                                         ${step {
      Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf)
    }}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(
      report.id,
      ActionEvent.EMAIL_PRO_REMIND_NO_READING
    )}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(
      report
    )}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(
      proUser.email,
      "Nouveau signalement",
      views.html.mails.professional
        .reportUnreadReminder(report, runningDateTime.plus(mailReminderDelay.multipliedBy(2)))
        .toString
    )}
    """
  }
}

class DontRemindUnreadWithAccessReport(implicit ee: ExecutionEnv) extends OnGoingWithAccessReportReminderTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = runningDateTime.minus(mailReminderDelay).plusDays(1))
    s2"""
         Given a pro user with activated account                                      ${step(setupUser(proUser))}
         Given a report with status "TRAITEMENT_EN_COURS" created less than 7 days    ${step(setupReport(report))}
         When remind task run                                                         ${step {
      Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf)
    }}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(
      report.id,
      List.empty
    )}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(
      report
    )}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent()}
    """
  }
}

class RemindTwiceUnreadWithAccessReport(implicit ee: ExecutionEnv) extends OnGoingWithAccessReportReminderTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = runningDateTime.minus(mailReminderDelay).minusDays(1))
    val event = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(1)))
    s2"""
         Given a pro user with activated account                                      ${step(setupUser(proUser))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(report))}
         Given a previous remind made more than 7 days                                ${step(setupEvent(event))}
         When remind task run                                                         ${step {
      Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf)
    }}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(
      report.id,
      ActionEvent.EMAIL_PRO_REMIND_NO_READING
    )}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(
      report
    )}
         And a mail is sent to the professional                                       ${mailMustHaveBeenSent(
      proUser.email,
      "Nouveau signalement",
      views.html.mails.professional.reportUnreadReminder(report, runningDateTime.plus(mailReminderDelay)).toString
    )}
    """
  }
}

class DontRemindTwiceUnreadWithAccessReport(implicit ee: ExecutionEnv) extends OnGoingWithAccessReportReminderTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = runningDateTime.minus(mailReminderDelay).minusDays(1))
    val event = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).plusDays(1)))
    s2"""
         Given a pro user with activated account                                      ${step(setupUser(proUser))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(report))}
         Given a previous remind made more than 7 days                                ${step(setupEvent(event))}
         When remind task run                                                         ${step {
      Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf)
    }}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(
      report.id,
      List(event)
    )}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(
      report
    )}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent()}
    """
  }
}

class CloseUnreadWithAccessReport(implicit ee: ExecutionEnv) extends OnGoingWithAccessReportReminderTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = runningDateTime.minus(mailReminderDelay).minusDays(1))
    val event1 = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(8)))
    val event2 = reminderEvent.copy(
      creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(1)),
      id = Some(UUID.randomUUID)
    )
    s2"""
         Given a pro user with activated account                                      ${step(setupUser(proUser))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(report))}
         Given twice previous remind made more than 7 days                            ${step(setupEvent(event1))}
                                                                                      ${step(setupEvent(event2))}
         When remind task run                                                         ${step {
      Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf)
    }}
         Then an event "NON_CONSULTE" is created                                      ${eventMustHaveBeenCreatedWithAction(
      report.id,
      ActionEvent.REPORT_CLOSED_BY_NO_READING
    )}
         And the report status is updated to "SIGNALEMENT_NON_CONSULTE"               ${reportMustHaveBeenUpdatedWithStatus(
      report.id,
      ReportStatus.SIGNALEMENT_NON_CONSULTE
    )}
         And a mail is sent to the consumer                                           ${mailMustHaveBeenSent(
      report.email,
      "L'entreprise n'a pas souhaité consulter votre signalement",
      views.html.mails.consumer.reportClosedByNoReading(report).toString,
      mailerService.attachmentSeqForWorkflowStepN(3)
    )}
   """
  }
}

class DontCloseUnreadWithAccessReport(implicit ee: ExecutionEnv) extends OnGoingWithAccessReportReminderTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = runningDateTime.minus(mailReminderDelay).minusDays(1))
    val event1 = reminderEvent.copy(creationDate = Some(runningDateTime.minus(mailReminderDelay).minusDays(8)))
    val event2 = reminderEvent.copy(
      creationDate = Some(runningDateTime.minus(mailReminderDelay).plusDays(1)),
      id = Some(UUID.randomUUID)
    )
    s2"""
         Given a pro user with activated account                                      ${step(setupUser(proUser))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(report))}
         Given a first remind made more than 7 days                                   ${step(setupEvent(event1))}
         Given a second remind made less than 7 days                                  ${step(setupEvent(event2))}
         When remind task run                                                         ${step {
      Await.result(reminderTask.runTask(runningDateTime.toLocalDateTime), Duration.Inf)
    }}
         Then no event is created                                                     ${eventMustNotHaveBeenCreated(
      report.id,
      List(event1, event2)
    )}
         And the report is not updated                                                ${reporStatustMustNotHaveBeenUpdated(
      report
    )}
         And no mail is sent                                                          ${mailMustNotHaveBeenSent()}
   """
  }
}

abstract class OnGoingWithAccessReportReminderTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Mockito
    with FutureMatchers {

  implicit val ec = ee.executionContext
  val mailReminderDelay = java.time.Period.parse(app.configuration.get[String]("play.reports.mailReminderDelay"))

  val runningDateTime = OffsetDateTime.now

  val proUser = Fixtures.genProUser.sample.get

  val company = Fixtures.genCompany.sample.get
  val onGoingReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      status = TRAITEMENT_EN_COURS
    )

  val reminderEvent = Fixtures.genEventForReport(onGoingReport.id, PRO, EMAIL_PRO_REMIND_NO_READING).sample.get

  def mailMustHaveBeenSent(
      recipient: EmailAddress,
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment] = Nil
  ) =
    there was one(mailerService)
      .sendEmail(
        EmailAddress(app.configuration.get[String]("play.mail.from")),
        Seq(recipient),
        Nil,
        subject,
        bodyHtml,
        attachments
      )

  def mailMustNotHaveBeenSent() =
    there was no(app.injector.instanceOf[MailerService])
      .sendEmail(
        EmailAddress(anyString),
        any[Seq[EmailAddress]],
        any[Seq[EmailAddress]],
        anyString,
        anyString,
        any
      )

  def eventMustHaveBeenCreatedWithAction(reportUUID: UUID, action: ActionEventValue) =
    eventRepository.getEvents(reportUUID, EventFilter(action = Some(action))).map(_.head) must eventActionMatcher(
      action
    ).await

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match $action")
  }

  def eventMustNotHaveBeenCreated(reportUUID: UUID, existingEvents: List[Event]) =
    eventRepository.getEvents(reportUUID, EventFilter()).map(_.length) must beEqualTo(existingEvents.length).await

  def reportMustHaveBeenUpdatedWithStatus(reportUUID: UUID, status: ReportStatusValue) =
    reportRepository.getReport(reportUUID) must reportStatusMatcher(status).await

  def reportStatusMatcher(status: ReportStatusValue): org.specs2.matcher.Matcher[Option[Report]] = {
    report: Option[Report] =>
      (report.map(report => status == report.status).getOrElse(false), s"status doesn't match $status")
  }

  def reporStatustMustNotHaveBeenUpdated(report: Report) =
    reportRepository.getReport(report.id).map(_.get.status) must beEqualTo(report.status).await

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val eventRepository = injector.instanceOf[EventRepository]
  lazy val reminderTask = injector.instanceOf[ReminderTask]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val accessTokenRepository = app.injector.instanceOf[AccessTokenRepository]
  lazy val mailerService = app.injector.instanceOf[MailerService]

  implicit lazy val websiteUrl = app.injector.instanceOf[Configuration].get[URI]("play.website.url")
  implicit lazy val contactAddress =
    app.injector.instanceOf[Configuration].get[EmailAddress]("play.mail.contactAddress")

  def setupUser(user: User) =
    Await.result(
      for {
        company <- companyRepository.getOrCreate(company.siret, company)
        admin <- userRepository.create(user)
        _ <- companyRepository.setUserLevel(company, admin, AccessLevel.ADMIN)
      } yield (),
      Duration.Inf
    )
  def setupReport(report: Report) =
    Await.result(reportRepository.create(report), Duration.Inf)
  def setupEvent(event: Event) =
    Await.result(eventRepository.createEvent(event), Duration.Inf)
}
