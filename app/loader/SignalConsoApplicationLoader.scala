package loader

import _root_.controllers._
import actors.ReportedPhonesExtractActor.ReportedPhonesExtractCommand
import actors._
import authentication._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config._
import models.report.sampledata.SampleDataService
import orchestrators._
import orchestrators.proconnect.ProConnectClient
import orchestrators.proconnect.ProConnectOrchestrator
import orchestrators.reportexport.ReportZipExportService
import orchestrators.socialmedia.InfluencerOrchestrator
import orchestrators.socialmedia.SocialBladeClient
import org.apache.pekko.actor.typed
import org.apache.pekko.actor.typed.DispatcherSelector
import org.apache.pekko.actor.typed.MailboxSelector
import org.apache.pekko.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import org.apache.pekko.util.Timeout
import org.flywaydb.core.Flyway
import play.api._
import play.api.db.slick.DbName
import play.api.db.slick.SlickComponents
import play.api.libs.mailer.MailerComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Cookie
import play.api.mvc.CookieHeaderEncoding
import play.api.mvc.EssentialFilter
import play.api.mvc.request.DefaultRequestFactory
import play.api.mvc.request.RequestFactory
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import pureconfig.ConfigConvert
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import repositories.accesstoken.AccessTokenRepository
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.albert.AlbertClassificationRepository
import repositories.asyncfiles.AsyncFileRepository
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.authattempt.AuthAttemptRepository
import repositories.authattempt.AuthAttemptRepositoryInterface
import repositories.authtoken.AuthTokenRepository
import repositories.authtoken.AuthTokenRepositoryInterface
import repositories.barcode.BarcodeProductRepository
import repositories.blacklistedemails.BlacklistedEmailsRepository
import repositories.blacklistedemails.BlacklistedEmailsRepositoryInterface
import repositories.bookmark.BookmarkRepository
import repositories.bookmark.BookmarkRepositoryInterface
import repositories.company.CompanyRepository
import repositories.company.CompanyRepositoryInterface
import repositories.company.CompanySyncRepository
import repositories.company.CompanySyncRepositoryInterface
import repositories.companyaccess.CompanyAccessRepository
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.companyactivationattempt.CompanyActivationAttemptRepository
import repositories.companyactivationattempt.CompanyActivationAttemptRepositoryInterface
import repositories.companyreportcounts.CompanyReportCountsRepository
import repositories.consumer.ConsumerRepository
import repositories.consumer.ConsumerRepositoryInterface
import repositories.consumerconsent.ConsumerConsentRepository
import repositories.emailvalidation.EmailValidationRepository
import repositories.emailvalidation.EmailValidationRepositoryInterface
import repositories.engagement.EngagementRepository
import repositories.event.EventRepository
import repositories.event.EventRepositoryInterface
import repositories.influencer.InfluencerRepository
import repositories.influencer.InfluencerRepositoryInterface
import repositories.ipblacklist.IpBlackListRepository
import repositories.probe.ProbeRepository
import repositories.proconnect.ProConnectSessionRepository
import repositories.proconnect.ProConnectSessionRepositoryInterface
import repositories.rating.RatingRepository
import repositories.rating.RatingRepositoryInterface
import repositories.report.ReportRepository
import repositories.report.ReportRepositoryInterface
import repositories.reportblockednotification.ReportNotificationBlockedRepository
import repositories.reportblockednotification.ReportNotificationBlockedRepositoryInterface
import repositories.reportconsumerreview.ResponseConsumerReviewRepository
import repositories.reportconsumerreview.ResponseConsumerReviewRepositoryInterface
import repositories.reportengagementreview.ReportEngagementReviewRepository
import repositories.reportengagementreview.ReportEngagementReviewRepositoryInterface
import repositories.reportfile.ReportFileRepository
import repositories.reportfile.ReportFileRepositoryInterface
import repositories.reportmetadata.ReportMetadataRepository
import repositories.reportmetadata.ReportMetadataRepositoryInterface
import repositories.siretextraction.SiretExtractionRepository
import repositories.socialnetwork.SocialNetworkRepository
import repositories.socialnetwork.SocialNetworkRepositoryInterface
import repositories.subcategorylabel.SubcategoryLabelRepository
import repositories.subscription.SubscriptionRepository
import repositories.subscription.SubscriptionRepositoryInterface
import repositories.tasklock.TaskRepository
import repositories.user.UserRepository
import repositories.user.UserRepositoryInterface
import repositories.website.WebsiteRepository
import repositories.website.WebsiteRepositoryInterface
import services._
import services.antivirus.AntivirusService
import services.antivirus.AntivirusServiceInterface
import services.emails.MailRetriesService
import services.emails.MailService
import services.emails.MailServiceInterface
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import tasks.EngagementEmailTask
import tasks.ExportReportsToSFTPTask
import tasks.ScheduledTask
import tasks.account.InactiveAccountTask
import tasks.account.InactiveDgccrfAccountReminderTask
import tasks.account.InactiveDgccrfAccountRemoveTask
import tasks.company._
import tasks.report._
import tasks.subcategorylabel.SubcategoryLabelTask
import tasks.website.SiretExtractionTask
import utils.CustomIpFilter
import utils.EmailAddress
import utils.FrontRoute
import utils.LoggingFilter

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.annotation.nowarn
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SignalConsoApplicationLoader() extends ApplicationLoader {
  var components: SignalConsoComponents = _

  override def load(context: ApplicationLoader.Context): Application = {
    components = new SignalConsoComponents(context)
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    components.scheduleTasks()
    components.application
  }
}

class SignalConsoComponents(
    context: ApplicationLoader.Context
) extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with play.filters.cors.CORSComponents
    with AssetsComponents
    with AhcWSComponents
    with SlickComponents
    with MailerComponents {

  val logger: Logger = Logger(this.getClass)

  implicit val localTimeInstance: ConfigConvert[LocalTime] = localTimeConfigConvert(DateTimeFormatter.ISO_TIME)
  implicit val personReader: ConfigReader[EmailAddress]    = deriveReader[EmailAddress]
  val csvStringListReader                                  = ConfigReader[String].map(_.split(",").toList)
  @nowarn("msg=Implicit definition should have explicit type")
  // scalafix:off
  implicit val stringListReader = ConfigReader[List[String]].orElse(csvStringListReader)
  // scalafix:on

  implicit val sameSiteReader: ConfigReader[Cookie.SameSite] = ConfigReader[String].map {
    case "strict" => Cookie.SameSite.Strict
    case "none"   => Cookie.SameSite.None
    case _        => Cookie.SameSite.Lax
  }

  val applicationConfiguration: ApplicationConfiguration = ConfigSource.default.loadOrThrow[ApplicationConfiguration]
  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()
  // Run database migration scripts
  Flyway
    .configure()
    .dataSource(
      applicationConfiguration.flyway.jdbcUrl,
      applicationConfiguration.flyway.user,
      applicationConfiguration.flyway.password
    )
    // DATA_LOSS / DESTRUCTIVE / BE AWARE ---- Keep to "false"
    // Be careful when enabling this as it removes the safety net that ensures Flyway does not migrate the wrong database in case of a configuration mistake!
    // This is useful for initial Flyway production deployments on projects with an existing DB.
    // See https://flywaydb.org/documentation/configuration/parameters/baselineOnMigrate for more information
    .baselineOnMigrate(applicationConfiguration.flyway.baselineOnMigrate)
    .load()
    .migrate()

  def emailConfiguration                                 = applicationConfiguration.mail
  def signalConsoConfiguration: SignalConsoConfiguration = applicationConfiguration.app
  def tokenConfiguration                                 = signalConsoConfiguration.token
  def uploadConfiguration: UploadConfiguration           = signalConsoConfiguration.upload

  def mobileAppConfiguration = signalConsoConfiguration.mobileApp

  def passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(
    current = new BCryptPasswordHasher(),
    deprecated = Seq.empty
  )

  //  Repositories

  val dbConfig: DatabaseConfig[JdbcProfile] = slickApi.dbConfig[JdbcProfile](DbName("default"))

  val taskRepository                                                    = new TaskRepository(dbConfig)
  val blacklistedEmailsRepository: BlacklistedEmailsRepositoryInterface = new BlacklistedEmailsRepository(dbConfig)
  val companyAccessRepository: CompanyAccessRepositoryInterface         = new CompanyAccessRepository(dbConfig)
  val accessTokenRepository: AccessTokenRepositoryInterface =
    new AccessTokenRepository(dbConfig, companyAccessRepository)
  val asyncFileRepository: AsyncFileRepositoryInterface                 = new AsyncFileRepository(dbConfig)
  val authAttemptRepository: AuthAttemptRepositoryInterface             = new AuthAttemptRepository(dbConfig)
  val authTokenRepository: AuthTokenRepositoryInterface                 = new AuthTokenRepository(dbConfig)
  val proConnectSessionRepository: ProConnectSessionRepositoryInterface = new ProConnectSessionRepository(dbConfig)
  def companyRepository: CompanyRepositoryInterface                     = new CompanyRepository(dbConfig)
  val companyActivationAttemptRepository: CompanyActivationAttemptRepositoryInterface =
    new CompanyActivationAttemptRepository(dbConfig)
  val consumerRepository: ConsumerRepositoryInterface =
    new ConsumerRepository(dbConfig)
  val emailValidationRepository: EmailValidationRepositoryInterface = new EmailValidationRepository(dbConfig)

  def eventRepository: EventRepositoryInterface                   = new EventRepository(dbConfig)
  val ratingRepository: RatingRepositoryInterface                 = new RatingRepository(dbConfig)
  val influencerRepository: InfluencerRepositoryInterface         = new InfluencerRepository(dbConfig)
  def reportRepository: ReportRepositoryInterface                 = new ReportRepository(dbConfig)
  val reportMetadataRepository: ReportMetadataRepositoryInterface = new ReportMetadataRepository(dbConfig)
  val bookmarkRepository: BookmarkRepositoryInterface             = new BookmarkRepository(dbConfig)
  val reportNotificationBlockedRepository: ReportNotificationBlockedRepositoryInterface =
    new ReportNotificationBlockedRepository(dbConfig)
  val responseConsumerReviewRepository: ResponseConsumerReviewRepositoryInterface =
    new ResponseConsumerReviewRepository(dbConfig)
  val reportEngagementReviewRepository: ReportEngagementReviewRepositoryInterface =
    new ReportEngagementReviewRepository(dbConfig)

  val consumerConsentRepository = new ConsumerConsentRepository(dbConfig)

  def reportFileRepository: ReportFileRepositoryInterface       = new ReportFileRepository(dbConfig)
  val subscriptionRepository: SubscriptionRepositoryInterface   = new SubscriptionRepository(dbConfig)
  def userRepository: UserRepositoryInterface                   = new UserRepository(dbConfig, passwordHasherRegistry)
  val websiteRepository: WebsiteRepositoryInterface             = new WebsiteRepository(dbConfig)
  val socialNetworkRepository: SocialNetworkRepositoryInterface = new SocialNetworkRepository(dbConfig)

  val engagementRepository = new EngagementRepository(dbConfig)

  val subcategoryLabelRepository = new SubcategoryLabelRepository(dbConfig)

  val ipBlackListRepository = new IpBlackListRepository(dbConfig)

  val albertClassificationRepository = new AlbertClassificationRepository(dbConfig)

  val siretExtractionRepository = new SiretExtractionRepository(dbConfig)

  val crypter = new JcaCrypter(applicationConfiguration.crypter)
  val signer  = new JcaSigner(applicationConfiguration.signer)

  val cookieAuthenticator =
    new CookieAuthenticator(signer, crypter, applicationConfiguration.cookie, userRepository)
  val apiKeyAuthenticator = new APIKeyAuthenticator(
    passwordHasherRegistry,
    consumerRepository,
    applicationConfiguration.apiConsumerTokenExpirationDelayInMonths
  )

  val credentialsProvider = new CredentialsProvider(passwordHasherRegistry, userRepository)

  lazy val s3Service: S3ServiceInterface = new S3Service(applicationConfiguration.amazonBucketName)

  val albertService = new AlbertService(applicationConfiguration.albert)

  //  Actor
  val antivirusScanActor: typed.ActorRef[AntivirusScanActor.ScanCommand] = actorSystem.spawn(
    AntivirusScanActor.create(uploadConfiguration, reportFileRepository, s3Service),
    "antivirus-scan-actor"
  )
  val reportedPhonesExtractActor: typed.ActorRef[ReportedPhonesExtractCommand] =
    actorSystem.spawn(
      ReportedPhonesExtractActor.create(signalConsoConfiguration, reportRepository, asyncFileRepository, s3Service),
      "reported-phones-extract-actor"
    )

  val websitesExtractActor: typed.ActorRef[WebsiteExtractActor.WebsiteExtractCommand] =
    actorSystem.spawn(
      WebsiteExtractActor.create(websiteRepository, asyncFileRepository, s3Service, signalConsoConfiguration),
      "websites-extract-actor"
    )

  val htmlConverterActor: typed.ActorRef[HtmlConverterActor.ConvertCommand] =
    actorSystem.spawn(
      HtmlConverterActor.create(),
      "html-converter-actor",
      DispatcherSelector.fromConfig("my-blocking-dispatcher")
    )

  val albertSummaryActor: typed.ActorRef[AlbertSummaryActor.AlbertSummaryCommand] =
    actorSystem.spawn(
      AlbertSummaryActor.create(albertService, albertClassificationRepository),
      "albert-summary-actor",
      MailboxSelector.bounded(5)
    )

  val pdfService                      = new PDFService(signalConsoConfiguration, htmlConverterActor)
  implicit val frontRoute: FrontRoute = new FrontRoute(signalConsoConfiguration)
  val attachmentService               = new AttachmentService(environment, pdfService, frontRoute)
  lazy val mailRetriesService         = new MailRetriesService(mailerClient, executionContext, actorSystem)
  var mailService = new MailService(
    mailRetriesService,
    emailConfiguration,
    reportNotificationBlockedRepository,
    pdfService,
    attachmentService
  )

  val siretExtractorService = new SiretExtractorService(applicationConfiguration.siretExtractor)

  // Orchestrator

  val userOrchestrator = new UserOrchestrator(userRepository, eventRepository)

  val proAccessTokenOrchestrator = new ProAccessTokenOrchestrator(
    userOrchestrator,
    companiesVisibilityOrchestrator,
    companyRepository,
    companyAccessRepository,
    accessTokenRepository,
    userRepository,
    eventRepository,
    mailService,
    frontRoute,
    tokenConfiguration
  )

  val accessesOrchestrator = new AccessesOrchestrator(
    userOrchestrator,
    accessTokenRepository,
    mailService,
    frontRoute,
    tokenConfiguration
  )

  val authOrchestrator = new AuthOrchestrator(
    authAttemptRepository,
    userRepository,
    accessesOrchestrator,
    authTokenRepository,
    tokenConfiguration,
    signalConsoConfiguration,
    credentialsProvider,
    mailService,
    cookieAuthenticator
  )

  def companiesVisibilityOrchestrator =
    new CompaniesVisibilityOrchestrator(companyRepository, companyAccessRepository, reportRepository)

  val companyAccessOrchestrator =
    new CompanyAccessOrchestrator(
      companyAccessRepository,
      companyRepository,
      accessTokenRepository,
      companyActivationAttemptRepository,
      eventRepository,
      proAccessTokenOrchestrator,
      userOrchestrator,
      userRepository
    )

  private val taskConfiguration: TaskConfiguration = applicationConfiguration.task
  val companyOrchestrator = new CompanyOrchestrator(
    companyRepository,
    companiesVisibilityOrchestrator,
    companyAccessOrchestrator,
    reportRepository,
    websiteRepository,
    accessTokenRepository,
    eventRepository,
    pdfService,
    taskConfiguration,
    frontRoute,
    emailConfiguration,
    tokenConfiguration,
    signalConsoConfiguration
  )

  val visibleReportOrchestrator = new VisibleReportOrchestrator(
    reportRepository,
    companyRepository,
    companiesVisibilityOrchestrator
  )

  val dataEconomieOrchestrator = new DataEconomieOrchestrator(reportRepository)
  val emailValidationOrchestrator =
    new EmailValidationOrchestrator(
      mailService,
      emailValidationRepository,
      emailConfiguration,
      messagesApi,
      consumerConsentRepository
    )

  val eventsOrchestrator =
    new EventsOrchestrator(visibleReportOrchestrator, eventRepository, companyRepository)

  val reportBlockedNotificationOrchestrator = new ReportBlockedNotificationOrchestrator(
    reportNotificationBlockedRepository
  )

  val reportConsumerReviewOrchestrator =
    new ReportConsumerReviewOrchestrator(
      visibleReportOrchestrator,
      reportRepository,
      eventRepository,
      responseConsumerReviewRepository
    )

  val accessesMassManagementOrchestrator = new AccessesMassManagementOrchestrator(
    companiesVisibilityOrchestrator,
    proAccessTokenOrchestrator,
    companyAccessOrchestrator,
    userOrchestrator,
    eventRepository
  )

  val htmlFromTemplateGenerator = new HtmlFromTemplateGenerator(messagesApi, frontRoute)

  def antivirusService: AntivirusServiceInterface =
    new AntivirusService(conf = signalConsoConfiguration.antivirusServiceConfiguration, backend)

  val engagementOrchestrator =
    new EngagementOrchestrator(
      engagementRepository,
      visibleReportOrchestrator,
      companiesVisibilityOrchestrator,
      eventRepository,
      reportRepository,
      reportEngagementReviewRepository
    )
  // Using a different thread pool on this one as it is very heavy on blocking IO and used exclusively for mass import
  val massReportZipExportService =
    new ReportZipExportService(htmlFromTemplateGenerator, pdfService, s3Service)(
      materializer,
      actorSystem.dispatchers.lookup("zip-blocking-dispatcher")
    )

  val reportZipExportService =
    new ReportZipExportService(htmlFromTemplateGenerator, pdfService, s3Service)(
      materializer,
      actorSystem.dispatchers.lookup("my-blocking-dispatcher")
    )

  val reportFileOrchestrator =
    new ReportFileOrchestrator(
      reportFileRepository,
      visibleReportOrchestrator,
      antivirusScanActor,
      s3Service,
      reportZipExportService,
      antivirusService
    )

  val bookmarkOrchestrator = new BookmarkOrchestrator(reportRepository, bookmarkRepository)

  val emailNotificationOrchestrator = new EmailNotificationOrchestrator(mailService, subscriptionRepository)

  private def buildReportOrchestrator(emailService: MailServiceInterface) = new ReportOrchestrator(
    emailService,
    reportRepository,
    reportMetadataRepository,
    reportFileOrchestrator,
    companyRepository,
    companyOrchestrator,
    socialNetworkRepository,
    accessTokenRepository,
    eventRepository,
    userRepository,
    websiteRepository,
    companiesVisibilityOrchestrator,
    emailNotificationOrchestrator,
    blacklistedEmailsRepository,
    emailValidationOrchestrator,
    emailConfiguration,
    tokenConfiguration,
    signalConsoConfiguration,
    companySyncService,
    engagementRepository,
    subcategoryLabelRepository,
    albertSummaryActor,
    messagesApi
  )

  val reportOrchestrator = buildReportOrchestrator(mailService)

  val reportWithDataOrchestrator =
    new ReportWithDataOrchestrator(
      companyRepository,
      eventRepository,
      reportOrchestrator,
      reportConsumerReviewOrchestrator,
      signalConsoConfiguration
    )

  val reportAssignmentOrchestrator = new ReportAssignmentOrchestrator(
    visibleReportOrchestrator,
    companiesVisibilityOrchestrator,
    mailService,
    reportMetadataRepository,
    userRepository,
    eventRepository
  )

  val socialBladeClient      = new SocialBladeClient(applicationConfiguration.socialBlade)
  val influencerOrchestrator = new InfluencerOrchestrator(influencerRepository, socialBladeClient)

  val reportsPdfExtractActor: typed.ActorRef[ReportsZipExtractActor.ReportsExtractCommand] =
    actorSystem.spawn(
      ReportsZipExtractActor.create(
        reportWithDataOrchestrator,
        asyncFileRepository,
        s3Service,
        massReportZipExportService
      ),
      "reports-zip-extract-actor"
    )

  val reportsExtractActor: typed.ActorRef[ReportsExtractActor.ReportsExtractCommand] =
    actorSystem.spawn(
      ReportsExtractActor.create(
        companyAccessRepository,
        reportOrchestrator,
        eventRepository,
        asyncFileRepository,
        s3Service,
        signalConsoConfiguration
      ),
      "reports-extract-actor"
    )

  val websiteApiService = new WebsiteApiService(applicationConfiguration.websiteApi)

  val statsOrchestrator =
    new StatsOrchestrator(
      reportRepository,
      eventRepository,
      responseConsumerReviewRepository,
      reportEngagementReviewRepository,
      accessTokenRepository,
      websiteApiService,
      subcategoryLabelRepository
    )

  val rgpdOrchestrator = new RgpdOrchestrator(
    reportConsumerReviewOrchestrator,
    engagementOrchestrator,
    reportRepository,
    reportFileOrchestrator,
    eventRepository,
    albertClassificationRepository
  )

  val reportAdminActionOrchestrator = new ReportAdminActionOrchestrator(
    mailService,
    reportConsumerReviewOrchestrator,
    engagementOrchestrator,
    reportRepository,
    reportFileOrchestrator,
    companyRepository,
    eventRepository,
    companiesVisibilityOrchestrator,
    rgpdOrchestrator,
    messagesApi
  )

  val probeRepository = new ProbeRepository(dbConfig)
  val probeOrchestrator = new ProbeOrchestrator(
    actorSystem,
    applicationConfiguration.task,
    taskRepository,
    probeRepository,
    reportRepository,
    userRepository,
    eventRepository,
    authAttemptRepository,
    reportFileRepository,
    emailValidationRepository,
    mailService
  )

  val websitesOrchestrator =
    new WebsitesOrchestrator(
      websiteRepository,
      companyRepository,
      companyOrchestrator,
      reportRepository,
      reportOrchestrator
    )

  val albertOrchestrator = new AlbertOrchestrator(
    reportRepository,
    albertService
  )

  val reportClosureTask = new ReportClosureTask(
    actorSystem,
    reportRepository,
    eventRepository,
    companyRepository,
    mailService,
    taskConfiguration,
    taskRepository,
    messagesApi
  )

  val orphanReportFileDeletionTask = new OrphanReportFileDeletionTask(
    actorSystem,
    reportFileRepository,
    s3Service,
    taskConfiguration,
    taskRepository
  )

  val oldReportExportDeletionTask = new OldReportExportDeletionTask(
    actorSystem,
    asyncFileRepository,
    s3Service,
    taskConfiguration,
    taskRepository
  )

  val oldReportsRgpdDeletionTask = new OldReportsRgpdDeletionTask(
    actorSystem,
    reportRepository,
    rgpdOrchestrator,
    taskConfiguration,
    taskRepository
  )

  val reportReminderTask = new ReportRemindersTask(
    actorSystem,
    reportRepository,
    eventRepository,
    mailService,
    companiesVisibilityOrchestrator,
    taskConfiguration,
    taskRepository
  )

  def companySyncService: CompanySyncServiceInterface = new CompanySyncService(
    applicationConfiguration.task.companyUpdate,
    backend
  )

  val companySyncRepository: CompanySyncRepositoryInterface = new CompanySyncRepository(dbConfig)
  private val companyUpdateTask = new CompanyUpdateTask(
    actorSystem,
    companyRepository,
    companySyncService,
    companySyncRepository,
    taskConfiguration,
    taskRepository
  )

  val reportNotificationTask =
    new ReportNotificationTask(
      actorSystem,
      reportRepository,
      subscriptionRepository,
      userRepository,
      mailService,
      taskConfiguration,
      taskRepository
    )

  val companyReportCountsRepository = new CompanyReportCountsRepository(dbConfig)

  val companyReportCountViewRefresherTask =
    new CompanyReportCountViewRefresherTask(
      actorSystem,
      companyReportCountsRepository,
      taskConfiguration,
      taskRepository
    )

  private val reportOrchestratorWithFakeMailer = buildReportOrchestrator(_ => Future.unit)

  val openFoodFactsService            = new OpenFoodFactsService
  val openBeautyFactsService          = new OpenBeautyFactsService
  val barcodeProductRepository        = new BarcodeProductRepository(dbConfig)
  def gs1Service: GS1ServiceInterface = new GS1Service(applicationConfiguration.gs1)
  val gs1AuthTokenActor: typed.ActorRef[actors.GS1AuthTokenActor.Command] = actorSystem.spawn(
    GS1AuthTokenActor(gs1Service),
    "gs1-auth-token-actor"
  )
  implicit val timeout: Timeout = 30.seconds

  val barcodeOrchestrator =
    new BarcodeOrchestrator(
      gs1AuthTokenActor,
      gs1Service,
      openFoodFactsService,
      openBeautyFactsService,
      barcodeProductRepository
    )

  val sampleDataService = new SampleDataService(
    companyRepository,
    userRepository,
    accessTokenRepository,
    reportOrchestratorWithFakeMailer,
    barcodeOrchestrator,
    reportRepository,
    companyAccessRepository,
    reportAdminActionOrchestrator,
    reportConsumerReviewOrchestrator,
    engagementOrchestrator,
    websiteRepository,
    eventRepository,
    engagementRepository,
    reportNotificationBlockedRepository
  )(
    actorSystem
  )

  val sampleDataGenerationTask =
    new SampleDataGenerationTask(actorSystem, sampleDataService, taskConfiguration, taskRepository)

  val inactiveDgccrfAccountRemoveTask =
    new InactiveDgccrfAccountRemoveTask(userRepository, subscriptionRepository, asyncFileRepository)

  val inactiveDgccrfAccountReminderTask =
    new InactiveDgccrfAccountReminderTask(
      userRepository,
      eventRepository,
      mailService
    )
  val inactiveAccountTask = new InactiveAccountTask(
    actorSystem,
    inactiveDgccrfAccountRemoveTask,
    inactiveDgccrfAccountReminderTask,
    applicationConfiguration.task,
    taskRepository
  )

  val engagementEmailTask = new EngagementEmailTask(
    mailService,
    companyRepository,
    engagementRepository,
    actorSystem,
    taskConfiguration,
    taskRepository,
    messagesApi
  )

  val exportReportsToSFTPTask = new ExportReportsToSFTPTask(
    actorSystem,
    taskConfiguration,
    taskRepository,
    reportRepository
  )

  val subcategoryLabelTask = new SubcategoryLabelTask(
    actorSystem,
    taskConfiguration,
    taskRepository,
    subcategoryLabelRepository,
    websiteApiService
  )

  val companyAlbertLabelTask = new CompanyAlbertLabelTask(
    actorSystem,
    taskConfiguration,
    companyRepository,
    albertOrchestrator,
    taskRepository
  )

  val siretExtractionTask = new SiretExtractionTask(
    actorSystem,
    taskConfiguration,
    taskRepository,
    siretExtractionRepository,
    siretExtractorService,
    websitesOrchestrator,
    companyRepository
  )

  // Controller

  val blacklistedEmailsController =
    new BlacklistedEmailsController(blacklistedEmailsRepository, cookieAuthenticator, controllerComponents)

  val accountController = new AccountController(
    userOrchestrator,
    userRepository,
    accessesOrchestrator,
    proAccessTokenOrchestrator,
    emailConfiguration,
    cookieAuthenticator,
    controllerComponents
  )

  val adminController = new AdminController(
    reportRepository,
    reportOrchestrator,
    companyAccessRepository,
    eventRepository,
    mailService,
    pdfService,
    emailConfiguration,
    taskConfiguration,
    companyRepository,
    emailNotificationOrchestrator,
    ipBlackListRepository,
    albertClassificationRepository,
    albertService,
    sampleDataService,
    subcategoryLabelRepository,
    frontRoute,
    cookieAuthenticator,
    controllerComponents
  )

  val socialNetworkController =
    new SocialNetworkController(influencerOrchestrator, cookieAuthenticator, controllerComponents)
  val asyncFileController =
    new AsyncFileController(asyncFileRepository, s3Service, cookieAuthenticator, controllerComponents)

  val proConnectClient = new ProConnectClient(applicationConfiguration.proConnect)
  val proConnectOrchestrator =
    new ProConnectOrchestrator(
      proConnectClient,
      proConnectSessionRepository,
      userOrchestrator,
      applicationConfiguration.proConnect.allowedProviderIds
    )

  val authController = new AuthController(
    authOrchestrator,
    cookieAuthenticator,
    controllerComponents,
    applicationConfiguration.app.enableRateLimit,
    proConnectOrchestrator
  )

  val companyAccessController =
    new CompanyAccessController(
      userRepository,
      companyRepository,
      companyAccessRepository,
      accessTokenRepository,
      companyOrchestrator,
      proAccessTokenOrchestrator,
      companiesVisibilityOrchestrator,
      companyAccessOrchestrator,
      cookieAuthenticator,
      controllerComponents
    )

  val companyController = new CompanyController(
    companyOrchestrator,
    companiesVisibilityOrchestrator,
    albertOrchestrator,
    cookieAuthenticator,
    controllerComponents
  )

  val accessesMassManagementController = new AccessesMassManagementController(
    companiesVisibilityOrchestrator,
    accessesMassManagementOrchestrator,
    cookieAuthenticator,
    controllerComponents
  )

  val constantController  = new ConstantController(cookieAuthenticator, controllerComponents)
  val mobileAppController = new MobileAppController(signalConsoConfiguration, cookieAuthenticator, controllerComponents)
  val dataEconomieController =
    new DataEconomieController(dataEconomieOrchestrator, apiKeyAuthenticator, controllerComponents)
  val emailValidationController =
    new EmailValidationController(cookieAuthenticator, emailValidationOrchestrator, controllerComponents)

  val eventsController = new EventsController(
    companyOrchestrator,
    companiesVisibilityOrchestrator,
    eventsOrchestrator,
    cookieAuthenticator,
    controllerComponents
  )
  val ratingController = new RatingController(ratingRepository, cookieAuthenticator, controllerComponents)
  val reportBlockedNotificationController =
    new ReportBlockedNotificationController(
      cookieAuthenticator,
      reportBlockedNotificationOrchestrator,
      controllerComponents
    )
  val reportConsumerReviewController =
    new ReportConsumerReviewController(reportConsumerReviewOrchestrator, cookieAuthenticator, controllerComponents)

  val reportFileController =
    new ReportFileController(
      reportFileOrchestrator,
      visibleReportOrchestrator,
      cookieAuthenticator,
      signalConsoConfiguration,
      controllerComponents
    )

  val healthController = new HealthController(cookieAuthenticator, controllerComponents)

  val reportController = new ReportController(
    reportOrchestrator,
    reportAssignmentOrchestrator,
    reportAdminActionOrchestrator,
    eventsOrchestrator,
    reportRepository,
    userRepository,
    reportFileRepository,
    companyRepository,
    pdfService,
    frontRoute,
    cookieAuthenticator,
    controllerComponents,
    reportWithDataOrchestrator,
    visibleReportOrchestrator,
    reportZipExportService
  )

  val reportedPhoneController = new ReportedPhoneController(
    reportRepository,
    companyRepository,
    asyncFileRepository,
    reportedPhonesExtractActor,
    cookieAuthenticator,
    controllerComponents
  )

  val reportListController =
    new ReportListController(
      reportOrchestrator,
      asyncFileRepository,
      reportsExtractActor,
      reportsPdfExtractActor,
      cookieAuthenticator,
      controllerComponents
    )

  val reportToExternalController =
    new ReportToExternalController(
      reportRepository,
      reportFileRepository,
      reportOrchestrator,
      reportFileOrchestrator,
      apiKeyAuthenticator,
      signalConsoConfiguration,
      controllerComponents
    )

  val statisticController =
    new StatisticController(
      companyOrchestrator,
      statsOrchestrator,
      companiesVisibilityOrchestrator,
      cookieAuthenticator,
      controllerComponents
    )

  val subscriptionOrchestrator = new SubscriptionOrchestrator(subscriptionRepository)
  val subscriptionController =
    new SubscriptionController(subscriptionOrchestrator, cookieAuthenticator, controllerComponents)
  val websiteController = new WebsiteController(
    websitesOrchestrator,
    companyRepository,
    websitesExtractActor,
    cookieAuthenticator,
    controllerComponents
  )

  val siretExtractorController =
    new SiretExtractorController(
      siretExtractionRepository,
      siretExtractorService,
      cookieAuthenticator,
      controllerComponents
    )

  val importOrchestrator = new ImportOrchestrator(
    companyRepository,
    companySyncService,
    userOrchestrator,
    companyOrchestrator,
    proAccessTokenOrchestrator,
    websiteRepository,
    websitesOrchestrator
  )
  val importController = new ImportController(
    importOrchestrator,
    cookieAuthenticator,
    controllerComponents
  )

  val barcodeController = new BarcodeController(barcodeOrchestrator, cookieAuthenticator, controllerComponents)

  val engagementController =
    new EngagementController(engagementOrchestrator, cookieAuthenticator, controllerComponents)

  val bookmarkController = new BookmarkController(bookmarkOrchestrator, cookieAuthenticator, controllerComponents)

  io.sentry.Sentry.captureException(
    new Exception("This is a test Alert, used to check that Sentry alert are still active on each new deployments.")
  )

  // Routes
  lazy val router: Router =
    new _root_.router.Routes(
      httpErrorHandler,
      healthController,
      assets,
      reportController,
      socialNetworkController,
      barcodeController,
      companyController,
      websiteController,
      reportConsumerReviewController,
      engagementController,
      emailValidationController,
      ratingController,
      constantController,
      reportFileController,
      reportListController,
      bookmarkController,
      asyncFileController,
      eventsController,
      adminController,
      statisticController,
      companyAccessController,
      authController,
      accountController,
      blacklistedEmailsController,
      accessesMassManagementController,
      importController,
      reportBlockedNotificationController,
      subscriptionController,
      siretExtractorController,
      reportedPhoneController,
      mobileAppController,
      reportToExternalController,
      dataEconomieController
    )

  val allTasks: List[ScheduledTask] =
    List(
      List(
        companyUpdateTask,
        reportNotificationTask,
        inactiveAccountTask,
        engagementEmailTask,
        exportReportsToSFTPTask,
        reportClosureTask,
        reportReminderTask,
        orphanReportFileDeletionTask,
        oldReportExportDeletionTask,
        oldReportsRgpdDeletionTask,
        subcategoryLabelTask,
        companyAlbertLabelTask,
        companyReportCountViewRefresherTask,
        siretExtractionTask
      ),
      if (applicationConfiguration.task.probe.active) {
        probeOrchestrator.buildAllTasks()
      } else
        Nil,
      if (applicationConfiguration.task.sampleData.active) {
        List(sampleDataGenerationTask)
      } else Nil
    ).flatten

  def scheduleTasks() =
    allTasks.foreach(_.schedule())

  override def config: Config = ConfigFactory.load()

  override def httpFilters: Seq[EssentialFilter] =
    Seq(
      new LoggingFilter(),
      new CustomIpFilter(ipBlackListRepository),
      securityHeadersFilter,
      allowedHostsFilter,
      corsFilter
    )

  override lazy val requestFactory: RequestFactory = new DefaultRequestFactory(httpConfiguration) {
    override val cookieHeaderEncoding: CookieHeaderEncoding = new CustomCookieHeaderEncoding(
      applicationConfiguration.cookie
    )
  }

}
