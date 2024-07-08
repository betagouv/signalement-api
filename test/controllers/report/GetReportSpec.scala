package controllers.report

import org.apache.pekko.util.Timeout
import loader.SignalConsoComponents
import models._
import models.company.AccessLevel
import models.company.CompanyWithAccess
import models.event.Event
import models.report._
import orchestrators.CompaniesVisibilityOrchestrator
import org.specs2.Spec
import org.specs2.concurrent.ExecutionEnv
import play.api.Application
import play.api.ApplicationLoader
import play.api.Configuration
import play.api.i18n.Lang
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.json.Json
import play.api.libs.mailer.Attachment
import play.api.mvc.Result
import play.api.test.Helpers.contentAsJson
import play.api.test._
import play.mvc.Http.Status
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportfile.ReportFileRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.MailRetriesService.EmailRequest
import services.emails.MailRetriesService
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils._
import utils.AuthHelpers._

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import java.time.temporal.ChronoUnit

object GetReportByUnauthenticatedUser extends GetReportSpec {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step { someUser = None }}
         When retrieving the report                                   ${step {
        someResult = Some(getReport(neverRequestedReport.id))
      }}
         Then user is not authorized                                  ${userMustBeUnauthorized()}
    """
}

object GetReportByAdminUser extends GetReportSpec {
  override def is =
    s2"""
         Given an authenticated admin user                            ${step { someUser = Some(adminUser) }}
         When retrieving the report                                   ${step {
        someResult = Some(getReport(neverRequestedReport.id))
      }}
         Then the report is rendered to the user as an Admin          ${reportMustBeRenderedForUserRole(
        neverRequestedReport,
        UserRole.Admin
      )}
    """
}

object GetReportByNotConcernedProUser extends GetReportSpec {
  override def is =
    s2"""
         Given an authenticated pro user which is not concerned by the report   ${step {
        someUser = Some(notConcernedProUser)
      }}
         When getting the report                                                ${step {
        someResult = Some(getReport(neverRequestedReport.id))
      }}
         Then the report is not found                                           ${reportMustBeNotFound()}
    """
}

object GetReportByConcernedProUserFirstTime extends GetReportSpec {
  implicit val messagesProvider: MessagesProvider =
    MessagesImpl(Lang(neverRequestedReport.lang.getOrElse(Locale.FRENCH)), messagesApi)

  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step {
        someUser = Some(concernedProUser)
      }}
         When retrieving the report for the first time                          ${step {
        someResult = Some(getReport(neverRequestedReport.id))
      }}
         Then an event "ENVOI_SIGNALEMENT is created                            ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.REPORT_READING_BY_PRO
      )}
         And the report reportStatusList is updated to "SIGNALEMENT_TRANSMIS"   ${reportStatusMustMatch(
        neverRequestedReport.id,
        ReportStatus.Transmis
      )}
         And a mail is sent to the consumer                                     ${mailMustHaveBeenSent(
        neverRequestedReport.email,
        "L'entreprise a pris connaissance de votre signalement",
        views.html.mails.consumer
          .reportTransmission(neverRequestedReport, Some(company))
          .toString,
        attachementService.attachmentSeqForWorkflowStepN(3, Locale.FRENCH)
      )}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(
        neverRequestedReport.copy(status = ReportStatus.Transmis),
        UserRole.Professionnel
      )}
      """
}

object GetFinalReportByConcernedProUserFirstTime extends GetReportSpec {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step {
        someUser = Some(concernedProUser)
      }}
         When retrieving a final report for the first time                      ${step {
        someResult = Some(getReport(neverRequestedFinalReport.id))
      }}
         Then an event "ENVOI_SIGNALEMENT is created                            ${eventMustHaveBeenCreatedWithAction(
        ActionEvent.REPORT_READING_BY_PRO
      )}
         And the report reportStatusList is not updated                         ${reportStatusMustMatch(
        neverRequestedFinalReport.id,
        neverRequestedFinalReport.status
      )}
         And no mail is sent                                                    ${mailMustNotHaveBeenSent()}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(
        neverRequestedFinalReport,
        UserRole.Professionnel
      )}
    """
}

object GetReportByConcernedProUserNotFirstTime extends GetReportSpec {
  override def is =
    s2"""
         Given an authenticated pro user which is concerned by the report       ${step {
        someUser = Some(concernedProUser)
      }}
         When retrieving the report not for the first time                      ${step {
        someResult = Some(getReport(alreadyRequestedReport.id))
      }}
         Then no event is created                                               ${eventMustNotHaveBeenCreated()}
         And the report reportStatusList is not updated                         ${reportStatusMustMatch(
        alreadyRequestedReport.id,
        alreadyRequestedReport.status
      )}
         And no mail is sent                                                    ${mailMustNotHaveBeenSent()}
         And the report is rendered to the user as a Professional               ${reportMustBeRenderedForUserRole(
        alreadyRequestedReport,
        UserRole.Professionnel
      )}

    """
}

trait GetReportSpec extends Spec with GetReportContext {

  import org.specs2.matcher.MatchersImplicits._

  implicit val ee: ExecutionEnv = ExecutionEnv.fromGlobalExecutionContext

  implicit val timeout: Timeout = 30.seconds

  lazy val messagesApi = components.messagesApi

  var someUser: Option[User]     = None
  var someResult: Option[Result] = None

  def getReport(reportUUID: UUID) =
    Await.result(
      components.reportController
        .getReport(reportUUID)
        .apply(
          someUser
            .map(user => FakeRequest().withAuthCookie(user.email, components.cookieAuthenticator))
            .getOrElse(FakeRequest())
        ),
      Duration.Inf
    )

  def userMustBeUnauthorized() =
    someResult.isDefined mustEqual true and someResult.get.header.status === Status.UNAUTHORIZED

  def reportMustBeNotFound() =
    someResult.isDefined mustEqual true and someResult.get.header.status === Status.NOT_FOUND

  def reportMustBeRenderedForUserRole(report: Report, userRole: UserRole) = {
    implicit val someUserRole: Option[UserRole] = Some(userRole)
    someResult.isDefined mustEqual true and contentAsJson(Future.successful(someResult.get)) === Json.toJson(
      ReportWithFiles(report, None, List.empty)
    )
  }

  def mailMustHaveBeenSent(
      recipient: EmailAddress,
      subject: String,
      bodyHtml: String,
      attachments: Seq[Attachment] = attachementService.defaultAttachments
  ) =
    there was one(mockMailRetriesService).sendEmailWithRetries(
      argThat((emailRequest: EmailRequest) =>
        emailRequest.recipients.sortBy(_.value).toList == List(recipient) &&
          emailRequest.subject === subject && emailRequest.bodyHtml === bodyHtml && emailRequest.attachments == attachments
      )
    )

  def mailMustNotHaveBeenSent() =
    there was no(mockMailRetriesService)
      .sendEmailWithRetries(
        any[EmailRequest]
      )

  def reportStatusMustMatch(id: UUID, status: ReportStatus) = {

    val maybeReport = Await.result(
      mockReportRepository.get(id),
      Duration.Inf
    )
    maybeReport.map(_ => status) mustEqual (Some(status))
  }

  def reportMustNotHaveBeenUpdated() =
    there was no(mockReportRepository).update(any[UUID], any[Report])

  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) =
    there was one(mockEventRepository).create(argThat(eventActionMatcher(action)))

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def eventMustNotHaveBeenCreated() =
    there was no(mockEventRepository).create(any[Event])

}

trait GetReportContext extends AppSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val siretForConcernedPro = Fixtures.genSiret().sample.get
  // TODO Check why not used

  val siretForNotConcernedPro = Fixtures.genSiret().sample.get

  val address = Fixtures.genAddress().sample.get

  val company = Fixtures.genCompany.sample.get.copy(address = address)

  private val valueGender: Option[Gender] = Fixtures.genGender.sample.get
  val neverRequestedReport = Report(
    gender = valueGender,
    category = "category",
    subcategories = List("subcategory"),
    details = List(),
    influencer = None,
    companyId = Some(company.id),
    companyName = Some("companyName"),
    companyBrand = Some("companyBrand"),
    companyCommercialName = Some("companyCommercialName"),
    companyEstablishmentCommercialName = Some("companyEstablishmentCommercialName"),
    companyAddress = company.address,
    companySiret = Some(company.siret),
    companyActivityCode = company.activityCode,
    websiteURL = WebsiteURL(None, None),
    phone = None,
    firstName = "firstName",
    lastName = "lastName",
    email = EmailAddress("email"),
    contactAgreement = true,
    employeeConsumer = false,
    status = ReportStatus.TraitementEnCours,
    expirationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusDays(20),
    visibleToPro = true,
    lang = None,
    barcodeProductId = None,
    train = None,
    station = None
  )

  val neverRequestedFinalReport = Report(
    gender = valueGender,
    category = "category",
    subcategories = List("subcategory"),
    details = List(),
    influencer = None,
    companyId = Some(company.id),
    companyName = Some("companyName"),
    companyCommercialName = Some("companyCommercialName"),
    companyEstablishmentCommercialName = Some("companyEstablishmentCommercialName"),
    companyBrand = Some("companyBrand"),
    companyAddress = company.address,
    companySiret = Some(company.siret),
    companyActivityCode = company.activityCode,
    websiteURL = WebsiteURL(None, None),
    phone = None,
    firstName = "firstName",
    lastName = "lastName",
    email = EmailAddress("email"),
    contactAgreement = true,
    employeeConsumer = false,
    status = ReportStatus.ConsulteIgnore,
    expirationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusDays(20),
    visibleToPro = true,
    lang = None,
    barcodeProductId = None,
    train = None,
    station = None
  )

  val alreadyRequestedReport = Report(
    gender = valueGender,
    category = "category",
    subcategories = List("subcategory"),
    details = List(),
    influencer = None,
    companyId = Some(company.id),
    companyName = Some("companyName"),
    companyCommercialName = Some("companyCommercialName"),
    companyEstablishmentCommercialName = Some("companyEstablishmentCommercialName"),
    companyBrand = Some("companyBrand"),
    companyAddress = company.address,
    companySiret = Some(company.siret),
    companyActivityCode = company.activityCode,
    websiteURL = WebsiteURL(None, None),
    phone = None,
    firstName = "firstName",
    lastName = "lastName",
    email = EmailAddress("email"),
    contactAgreement = true,
    employeeConsumer = false,
    status = ReportStatus.Transmis,
    expirationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusDays(20),
    visibleToPro = true,
    lang = None,
    barcodeProductId = None,
    train = None,
    station = None
  )

  val adminUser = Fixtures.genAdminUser.sample.get

  val concernedProUser = Fixtures.genProUser.sample.get

  val notConcernedProUser = Fixtures.genProUser.sample.get

  val mockReportRepository = new ReportRepositoryMock()

  val mockCompanyRepository = new CompanyRepositoryMock()

  mockCompanyRepository.create(company)
  mockReportRepository.create(neverRequestedReport)
  mockReportRepository.create(neverRequestedFinalReport)
  mockReportRepository.create(alreadyRequestedReport)

  val mockReportFileRepository            = mock[ReportFileRepositoryInterface]
  val mockEventRepository                 = mock[EventRepositoryInterface]
  val mockMailRetriesService              = mock[MailRetriesService]
  val mockCompaniesVisibilityOrchestrator = mock[CompaniesVisibilityOrchestrator]
  val mockUserRepository                  = mock[UserRepositoryInterface]

  mockCompaniesVisibilityOrchestrator.fetchVisibleCompanies(any[User]) answers { (pro: Any) =>
    Future.successful(
      if (pro.asInstanceOf[User].id == concernedProUser.id) List(CompanyWithAccess(company, AccessLevel.ADMIN))
      else List()
    )
  }

  mockReportFileRepository.retrieveReportFiles(any[UUID]) returns Future.successful(List.empty)

  mockEventRepository.create(any[Event]) answers { (event: Any) => Future.successful(event.asInstanceOf[Event]) }
  mockEventRepository.getEvents(neverRequestedReport.id, EventFilter(None)) returns Future.successful(List.empty)
  mockEventRepository.getEvents(neverRequestedFinalReport.id, EventFilter(None)) returns Future.successful(List.empty)
  mockEventRepository.getEvents(alreadyRequestedReport.id, EventFilter(None)) returns Future.successful(
    List(
      Event(
        UUID.randomUUID(),
        Some(alreadyRequestedReport.id),
        Some(company.id),
        Some(concernedProUser.id),
        OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
        EventType.PRO,
        ActionEvent.REPORT_READING_BY_PRO
      )
    )
  )

  mockUserRepository.findByEmail(adminUser.email.value) returns Future.successful(
    Some(AuthHelpers.encryptUser(adminUser))
  )
  mockUserRepository.findByEmail(concernedProUser.email.value) returns Future.successful(
    Some(AuthHelpers.encryptUser(concernedProUser))
  )
  mockUserRepository.findByEmail(notConcernedProUser.email.value) returns Future.successful(
    Some(AuthHelpers.encryptUser(notConcernedProUser))
  )

  class FakeApplicationLoader extends ApplicationLoader {
    var components: SignalConsoComponents = _

    override def load(context: ApplicationLoader.Context): Application = {
      components = new SignalConsoComponents(context) {

        override def reportRepository: ReportRepositoryInterface         = mockReportRepository
        override def companyRepository: CompanyRepositoryInterface       = mockCompanyRepository
        override def reportFileRepository: ReportFileRepositoryInterface = mockReportFileRepository
        override lazy val mailRetriesService: MailRetriesService         = mockMailRetriesService
        override def eventRepository: EventRepositoryInterface           = mockEventRepository
        override def companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator =
          mockCompaniesVisibilityOrchestrator
        override def userRepository: UserRepositoryInterface = mockUserRepository

        override def configuration: Configuration = Configuration(
          "slick.dbs.default.db.connectionPool" -> "disabled",
          "play.mailer.mock"                    -> true
        ).withFallback(
          super.configuration
        )

      }
      components.application
    }

  }

  val appLoader                         = new FakeApplicationLoader()
  val app: Application                  = TestApp.buildApp(appLoader)
  val components: SignalConsoComponents = appLoader.components

  lazy val attachementService = components.attachmentService
  lazy val mailService        = components.mailService

}
