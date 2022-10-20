package tasks.report

import models.company.AccessLevel
import models.report.Report
import models.report.ReportStatus
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.mvc.Results
import play.api.test.WithApplication
import repositories.event.EventFilter
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class ReportReminderTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  lazy val reportRepository = components.reportRepository
  lazy val companyRepository = components.companyRepository
  lazy val userRepository = components.userRepository
  lazy val companyAccessRepository = components.companyAccessRepository
  lazy val reportReminderTask = components.reportReminderTask
  lazy val eventRepository = components.eventRepository

  val creationDate = OffsetDateTime.parse("2020-01-01T00:00:00Z")
  val taskRunDate = OffsetDateTime.parse("2020-06-01T00:00:00Z")
  val dateInThePast = taskRunDate.minusDays(5)
  val dateInTheFuture = taskRunDate.plusDays(5)

  def buildReportWithCompanyAndUsers(status: ReportStatus = ReportStatus.TraitementEnCours): Future[Report] = {
    val company = Fixtures.genCompany.sample.get
    val proUser = Fixtures.genProUser.sample.get
    val report = Fixtures.genReportForCompany(company).sample.get.copy(status = status)
    for {
      finalCompany <- companyRepository.create(company)
      finalProUser <- userRepository.create(proUser)
      _ <- companyAccessRepository.createUserAccess(
        finalCompany.id,
        finalProUser.id,
        AccessLevel.MEMBER
      )
      finalReport <- reportRepository.create(report)
    } yield finalReport
  }

  def hasNewEvent(newEventsCutoffDate: OffsetDateTime, report: Report, action: ActionEventValue): Future[Boolean] =
    eventRepository
      .getEvents(report.id, EventFilter(action = Some(action)))
      .map(_.filter(_.creationDate.isAfter(newEventsCutoffDate)))
      .map(_.nonEmpty)

  def hasZeroEvents(newEventsCutoffDate: OffsetDateTime, report: Report): Future[Boolean] =
    eventRepository
      .getEvents(report.id, EventFilter())
      .map(_.filter(_.creationDate.isAfter(newEventsCutoffDate)))
      .map(_.isEmpty)

  "ReportReminderTask should send reminders for the emails that need it" >> {

    // signalement en cours
    // signalement qui a eu 2 reminders déjà
    // signalement qui a eu un reminder très récent
    // signalement qui a eu un reminder assez ancien

    new WithApplication(app) {
      Await.result(
        for {
          // Setup
          ongoingReport <- buildReportWithCompanyAndUsers()
          // Run
          newEventsCutoffDate = OffsetDateTime.now()
          _ <- reportReminderTask.runTask(taskRunDate)
          // Check
//          allEventsForReport <- eventRepository
//            .getEvents(ongoingReport.id)
//          _ = println("EVENTS")
//          _ = println(allEventsForReport.sortBy(_.creationDate).map(e => e.creationDate -> e.action))
          _ <- hasNewEvent(newEventsCutoffDate, ongoingReport, EMAIL_PRO_REMIND_NO_READING) map (_ must beTrue)
        } yield (),
        Duration.Inf
      )
    }
  }
}
