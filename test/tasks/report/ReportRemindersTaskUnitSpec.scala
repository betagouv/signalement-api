package tasks.report

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import config.ExportReportsToSFTPConfiguration
import config.ProbeConfiguration
import config.ReportRemindersTaskConfiguration
import config.SampleDataConfiguration
import config.SubcategoryLabelsTaskConfiguration
import config.TaskConfiguration
import models.company.AccessLevel
import models.event.Event
import models.report.ReportStatus
import orchestrators.CompaniesVisibilityOrchestrator
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito.any
import org.specs2.mock.Mockito.mock
import org.specs2.mutable.Specification
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.emails.EmailDefinitionsPro.ProReportsReadReminder
import services.emails.EmailDefinitionsPro.ProReportsUnreadReminder
import services.emails.MailServiceInterface
import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.EventType
import utils.Fixtures
import utils.SIREN
import utils.TaskRepositoryMock

import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ReportRemindersTaskUnitSpec extends Specification with FutureMatchers {

  val taskRepositoryMock = new TaskRepositoryMock()

  val taskConf = TaskConfiguration(
    active = true,
    subscription = null,
    reportClosure = null,
    orphanReportFileDeletion = null,
    oldReportExportDeletion = null,
    oldReportsRgpdDeletion = null,
    reportReminders = ReportRemindersTaskConfiguration(
      startTime = LocalTime.of(2, 0),
      intervalInHours = 1.day,
      mailReminderDelay = Period.ofDays(7)
    ),
    inactiveAccounts = null,
    companyUpdate = null,
    probe = ProbeConfiguration(false),
    exportReportsToSFTP = ExportReportsToSFTPConfiguration("./reports.csv", LocalTime.of(3, 30)),
    subcategoryLabels = SubcategoryLabelsTaskConfiguration(startTime = LocalTime.of(2, 0), interval = 1.day),
    sampleData = SampleDataConfiguration(true, LocalTime.of(3, 30))
  )

  val testKit = ActorTestKit()

  def argMatching[T](pf: PartialFunction[Any, Unit]) = argThat[T](pf.isDefinedAt(_))

  "ReportReminderTask" should {
    "successfully send grouped emails by company" in {
      val reportRepository        = mock[ReportRepositoryInterface]
      val eventRepository         = mock[EventRepositoryInterface]
      val mailService             = mock[MailServiceInterface]
      val companyRepository       = mock[CompanyRepositoryInterface]
      val companyAccessRepository = mock[CompanyAccessRepositoryInterface]
      val companiesVisibilityOrchestrator =
        new CompaniesVisibilityOrchestrator(companyRepository, companyAccessRepository)

      val reportRemindersTask =
        new ReportRemindersTask(
          testKit.system.classicSystem,
          reportRepository,
          eventRepository,
          mailService,
          companiesVisibilityOrchestrator,
          taskConf,
          taskRepositoryMock
        )

      val company1 = Fixtures.genCompany.sample.get
      val company2 = Fixtures.genCompany.sample.get
      val report1  = Fixtures.genReportForCompany(company1).sample.get.copy(status = ReportStatus.TraitementEnCours)
      val report2  = Fixtures.genReportForCompany(company1).sample.get.copy(status = ReportStatus.TraitementEnCours)
      val report3  = Fixtures.genReportForCompany(company1).sample.get.copy(status = ReportStatus.Transmis)
      val report4  = Fixtures.genReportForCompany(company2).sample.get.copy(status = ReportStatus.TraitementEnCours)
      val report5  = Fixtures.genReportForCompany(company2).sample.get.copy(status = ReportStatus.Transmis)
      val report6  = Fixtures.genReportForCompany(company2).sample.get.copy(status = ReportStatus.Transmis)
      val report7  = Fixtures.genReportForCompany(company2).sample.get.copy(status = ReportStatus.Transmis)
      val proUser1 = Fixtures.genProUser.sample.get

      val event3 = Fixtures.genEventForReport(report3.id, eventType = EventType.SYSTEM, EMAIL_PRO_NEW_REPORT).sample.get
      val event7 =
        Fixtures.genEventForReport(report7.id, eventType = EventType.SYSTEM, EMAIL_PRO_REMIND_NO_READING).sample.get

      when(reportRepository.getByStatus(List(ReportStatus.TraitementEnCours, ReportStatus.Transmis)))
        .thenReturn(Future.successful(List(report1, report2, report3, report4, report5, report6, report7)))
      when(
        companyAccessRepository.fetchUsersByCompanyIds(eqTo(List(company1.id, company2.id)), any[Seq[AccessLevel]]())
      ).thenReturn(Future.successful(Map(company1.id -> List(proUser1), company2.id -> List(proUser1))))
      when(
        companyRepository.findHeadOffices(
          List(SIREN.fromSIRET(company1.siret), SIREN.fromSIRET(company2.siret)),
          openOnly = false
        )
      ).thenReturn(Future.successful(List.empty))
      when(companyAccessRepository.fetchUsersByCompanyIds(eqTo(List.empty), any[Seq[AccessLevel]]()))
        .thenReturn(Future.successful(Map.empty))
      when(eventRepository.fetchEventsOfReports(List(report1, report2, report3, report4, report5, report6, report7)))
        .thenReturn(Future.successful(Map(report3.id -> List(event3), report7.id -> List(event7))))

      when(
        mailService.send(
          ProReportsUnreadReminder.Email(List(proUser1.email), List(report1, report2), Period.ofDays(7))
        )
      ).thenReturn(Future.unit)
      when(mailService.send(ProReportsUnreadReminder.Email(List(proUser1.email), List(report4), Period.ofDays(7))))
        .thenReturn(Future.unit)
      when(
        mailService.send(
          ProReportsReadReminder.Email(List(proUser1.email), List(report5, report6), Period.ofDays(7))
        )
      ).thenReturn(Future.unit)

      when(eventRepository.create(argMatching[Event] { case Event(_, Some(report1.id), _, _, _, _, _, _) => }))
        .thenReturn(Future.successful(null))
      when(eventRepository.create(argMatching[Event] { case Event(_, Some(report2.id), _, _, _, _, _, _) => }))
        .thenReturn(Future.successful(null))
      when(eventRepository.create(argMatching[Event] { case Event(_, Some(report4.id), _, _, _, _, _, _) => }))
        .thenReturn(Future.successful(null))
      when(eventRepository.create(argMatching[Event] { case Event(_, Some(report5.id), _, _, _, _, _, _) => }))
        .thenReturn(Future.successful(null))
      when(eventRepository.create(argMatching[Event] { case Event(_, Some(report6.id), _, _, _, _, _, _) => }))
        .thenReturn(Future.successful(null))

      val futureRes = reportRemindersTask.runTask(OffsetDateTime.now())
      val res       = Await.result(futureRes, 1.second)

      res._1.length shouldEqual 0 // 0 failures
      res._2.length shouldEqual 3 // 3 emails sent
    }
  }
}
