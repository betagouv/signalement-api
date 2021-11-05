package tasks

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import models._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import play.api.libs.mailer.Attachment
import repositories._
import services.MailerService
import utils.AppSpec
import utils.EmailAddress
import utils.Fixtures
import utils.FrontRoute
import utils.Constants.ActionEvent
import utils.Constants.ActionEvent.ActionEventValue

import scala.concurrent.Await
import scala.concurrent.duration._

class CloseUnreadNoAccessReport(implicit ee: ExecutionEnv) extends UnreadNoAccessReportClosingTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = OffsetDateTime.now.minus(noAccessReadingDelay).minusDays(1))
    s2"""
       Given a company with no activated accout
       Given a report with status "ReportStatus2.TraitementEnCours" and expired reading delay   ${step(setupReport(report))}
       When remind task run                                                         ${step {
      Await.result(reminderTask.runTask(runningDateTime), Duration.Inf)
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

class DontCloseUnreadNoAccessReport(implicit ee: ExecutionEnv) extends UnreadNoAccessReportClosingTaskSpec {
  override def is = {
    val report = onGoingReport.copy(creationDate = OffsetDateTime.now.minus(noAccessReadingDelay).plusDays(1))
    s2"""
       Given a company with no activated accout
       Given a report with status "ReportStatus2.TraitementEnCours" and no expired reading delay    ${step(setupReport(report))}
       When remind task run                                                             ${step {
      Await.result(reminderTask.runTask(runningDateTime), Duration.Inf)
    }}
       Then no event is created                                                         ${eventMustNotHaveBeenCreated(
      report.id,
      List.empty
    )}
       And the report is not updated                                                    ${reporStatustMustNotHaveBeenUpdated(
      report
    )}
       And no mail is sent                                                              ${mailMustNotHaveBeenSent()}
    """
  }
}

abstract class UnreadNoAccessReportClosingTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Mockito
    with FutureMatchers {

  implicit lazy val frontRoute = injector.instanceOf[FrontRoute]

  implicit val ec = ee.executionContext

  val runningDateTime = LocalDateTime.now
  val noAccessReadingDelay = config.report.noAccessReadingDelay

  val company = Fixtures.genCompany.sample.get
  val onGoingReport = Fixtures
    .genReportForCompany(company)
    .sample
    .get
    .copy(
      status = ReportStatus2.TraitementEnCours
    )

  def mailMustHaveBeenSent(
      recipient: EmailAddress,
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment] = Nil
  ) =
    there was one(mailerService)
      .sendEmail(
        config.mail.from,
        Seq(recipient),
        Nil,
        subject,
        bodyHtml,
        attachments
      )

  def mailMustNotHaveBeenSent() =
    there was no(mailerService)
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
    (action == event.action, s"action doesn't match ${action}")
  }

  def eventMustNotHaveBeenCreated(reportUUID: UUID, existingEvents: List[Event]) =
    eventRepository.getEvents(reportUUID, EventFilter()).map(_.length) must beEqualTo(existingEvents.length).await

  def reportMustHaveBeenUpdatedWithStatus(reportUUID: UUID, status: ReportStatus2) =
    reportRepository.getReport(reportUUID) must reportStatusMatcher(status).await

  def reportStatusMatcher(status: ReportStatus2): org.specs2.matcher.Matcher[Option[Report]] = {
    report: Option[Report] =>
      (report.map(report => status == report.status).getOrElse(false), s"status doesn't match ${status}")
  }

  def reporStatustMustNotHaveBeenUpdated(report: Report) =
    reportRepository.getReport(report.id).map(_.get.status) must beEqualTo(report.status).await

  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val eventRepository = injector.instanceOf[EventRepository]
  lazy val reminderTask = injector.instanceOf[ReminderTask]
  lazy val mailerService = app.injector.instanceOf[MailerService]

  def setupReport(report: Report) =
    Await.result(reportRepository.create(report), Duration.Inf)

  override def setupData(): Unit =
    Await.result(companyRepository.getOrCreate(company.siret, company), Duration.Inf)
}
