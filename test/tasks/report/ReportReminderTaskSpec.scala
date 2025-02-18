package tasks.report

import models.company.AccessLevel
import models.report.Report
import models.report.ReportStatus
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.mvc.Results
import play.api.test.WithApplication
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.EventType
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class ReportReminderTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  lazy val reportRepository        = components.reportRepository
  lazy val companyRepository       = components.companyRepository
  lazy val userRepository          = components.userRepository
  lazy val companyAccessRepository = components.companyAccessRepository
  lazy val reportReminderTask      = components.reportReminderTask
  lazy val eventRepository         = components.eventRepository

  val creationDate     = OffsetDateTime.parse("2020-01-01T00:00:00Z")
  val taskRunDate      = OffsetDateTime.parse("2020-06-01T00:00:00Z")
  val date20DaysBefore = taskRunDate.minusDays(20)
  val date10DaysBefore = taskRunDate.minusDays(10)
  val date2DaysBefore  = taskRunDate.minusDays(2)

  def buildReportWithCompanyAndUserAndPastEvents(
      status: ReportStatus = ReportStatus.TraitementEnCours,
      withUser: Boolean = true,
      events: List[(ActionEventValue, OffsetDateTime)] = Nil
  ): Future[Report] = {
    val company = Fixtures.genCompany.sample.get
    val proUser = Fixtures.genProUser.sample.get
    val report  = Fixtures.genReportForCompany(company).sample.get.copy(status = status)
    for {
      finalCompany <- companyRepository.create(company)
      _ <-
        if (withUser) {
          for {
            finalProUser <- userRepository.create(proUser)
            _ <- companyAccessRepository.createUserAccess(
              finalCompany.id,
              finalProUser.id,
              AccessLevel.MEMBER
            )
          } yield ()
        } else Future.unit
      finalReport <- reportRepository.create(report)
      _ <- Future.sequence(events.map { case (eventAction, creationDate) =>
        val event = Fixtures
          .genEventForReport(finalReport.id, eventType = EventType.SYSTEM, eventAction)
          .sample
          .get
          .copy(creationDate = creationDate)
        eventRepository.create(event)
      })
    } yield finalReport
  }

  def getNewEvents(newEventsCutoffDate: OffsetDateTime, report: Report) =
    eventRepository
      .getEvents(report.id)
      .map(_.filter(_.creationDate.isAfter(newEventsCutoffDate)))

  def hasNewEvent(newEventsCutoffDate: OffsetDateTime, report: Report, action: ActionEventValue): Future[Boolean] =
    getNewEvents(newEventsCutoffDate, report)
      .map(_.filter(_.action == action))
      .map(_.nonEmpty)

  def hasZeroNewEvents(newEventsCutoffDate: OffsetDateTime, report: Report): Future[Boolean] =
    getNewEvents(newEventsCutoffDate, report)
      .map(_.isEmpty)

  "ReportReminderTask should send reminders for the emails that need it" >> {

    new WithApplication(app) {
      Await.result(
        for {
          // Setup
          ongoingReport           <- buildReportWithCompanyAndUserAndPastEvents()
          ongoingReportWasRead    <- buildReportWithCompanyAndUserAndPastEvents(status = ReportStatus.Transmis)
          ongoingReportWithNoUser <- buildReportWithCompanyAndUserAndPastEvents(withUser = false)
          ongoingReportWithMaxRemindersAlready <- buildReportWithCompanyAndUserAndPastEvents(events =
            List(
              (EMAIL_PRO_REMIND_NO_READING, date20DaysBefore),
              (EMAIL_PRO_REMIND_NO_READING, date20DaysBefore)
            )
          )
          ongoingReportWithOneRecentReminder <- buildReportWithCompanyAndUserAndPastEvents(events =
            List(
              (EMAIL_PRO_REMIND_NO_READING, date2DaysBefore)
            )
          )
          ongoingReportWithOneOldReminder <- buildReportWithCompanyAndUserAndPastEvents(events =
            List(
              (EMAIL_PRO_REMIND_NO_READING, date10DaysBefore)
            )
          )
          ongoingReportWithRecentNewReportEmail <- buildReportWithCompanyAndUserAndPastEvents(events =
            List(
              (EMAIL_PRO_NEW_REPORT, date2DaysBefore)
            )
          )
          ongoingReportWithOldNewReportEmail <- buildReportWithCompanyAndUserAndPastEvents(events =
            List(
              (EMAIL_PRO_NEW_REPORT, date10DaysBefore)
            )
          )
          ongoingReportWithNewReportEmailAndOneReminder <- buildReportWithCompanyAndUserAndPastEvents(events =
            List(
              (EMAIL_PRO_NEW_REPORT, date20DaysBefore),
              (EMAIL_PRO_REMIND_NO_READING, date10DaysBefore)
            )
          )

          // Run
          newEventsCutoffDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
          _ <- reportReminderTask.runTask(taskRunDate)

          _ <- hasNewEvent(newEventsCutoffDate, ongoingReport, EMAIL_PRO_REMIND_NO_READING) map (_ must beTrue)
          _ <- hasNewEvent(newEventsCutoffDate, ongoingReportWasRead, EMAIL_PRO_REMIND_NO_ACTION) map (_ must beTrue)
          _ <- hasZeroNewEvents(newEventsCutoffDate, ongoingReportWithNoUser) map (_ must beTrue)
          _ <- hasZeroNewEvents(newEventsCutoffDate, ongoingReportWithMaxRemindersAlready) map (_ must beTrue)
          _ <- hasZeroNewEvents(newEventsCutoffDate, ongoingReportWithOneRecentReminder) map (_ must beTrue)
          _ <- hasNewEvent(
            newEventsCutoffDate,
            ongoingReportWithOneOldReminder,
            EMAIL_PRO_REMIND_NO_READING
          ) map (_ must beTrue)
          _ <- hasZeroNewEvents(newEventsCutoffDate, ongoingReportWithRecentNewReportEmail) map (_ must beTrue)
          _ <- hasNewEvent(
            newEventsCutoffDate,
            ongoingReportWithOldNewReportEmail,
            EMAIL_PRO_REMIND_NO_READING
          ) map (_ must beTrue)
          _ <- hasNewEvent(
            newEventsCutoffDate,
            ongoingReportWithNewReportEmailAndOneReminder,
            EMAIL_PRO_REMIND_NO_READING
          ) map (_ must beTrue)

        } yield (),
        Duration.Inf
      )
    }
  }
}
