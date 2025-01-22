package loader

import _root_.controllers._
import actors.ReportedPhonesExtractActor.ReportedPhonesExtractCommand
import actors._
import org.apache.pekko.actor.typed
import org.apache.pekko.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import org.apache.pekko.util.Timeout
import authentication.CookieAuthenticator
import authentication._
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config._
import models.report.sampledata.SampleDataService
import orchestrators._
import orchestrators.proconnect.ProConnectClient
import orchestrators.proconnect.ProConnectOrchestrator
import orchestrators.socialmedia.InfluencerOrchestrator
import orchestrators.socialmedia.SocialBladeClient
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
import repositories.signalconsoreview.SignalConsoReviewRepository
import repositories.signalconsoreview.SignalConsoReviewRepositoryInterface
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
import tasks.account.InactiveAccountTask
import tasks.account.InactiveDgccrfAccountReminderTask
import tasks.account.InactiveDgccrfAccountRemoveTask
import tasks.company._
import tasks.report.OldReportExportDeletionTask
import tasks.report.OldReportsRgpdDeletionTask
import tasks.report.OrphanReportFileDeletionTask
import tasks.report.ReportClosureTask
import tasks.report.ReportNotificationTask
import tasks.report.ReportRemindersTask
import tasks.report.SampleDataGenerationTask
import tasks.subcategorylabel.SubcategoryLabelTask
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
  val consumerRepository: ConsumerRepositoryInterface               = new ConsumerRepository(dbConfig)
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
  def reportFileRepository: ReportFileRepositoryInterface       = new ReportFileRepository(dbConfig)
  val subscriptionRepository: SubscriptionRepositoryInterface   = new SubscriptionRepository(dbConfig)
  def userRepository: UserRepositoryInterface                   = new UserRepository(dbConfig, passwordHasherRegistry)
  val websiteRepository: WebsiteRepositoryInterface             = new WebsiteRepository(dbConfig)
  val socialNetworkRepository: SocialNetworkRepositoryInterface = new SocialNetworkRepository(dbConfig)

  val signalConsoReviewRepository: SignalConsoReviewRepositoryInterface = new SignalConsoReviewRepository(dbConfig)

  val engagementRepository = new EngagementRepository(dbConfig)

  val subcategoryLabelRepository = new SubcategoryLabelRepository(dbConfig)

  val ipBlackListRepository = new IpBlackListRepository(dbConfig)

  val albertClassificationRepository = new AlbertClassificationRepository(dbConfig)

  val crypter = new JcaCrypter(applicationConfiguration.crypter)
  val signer  = new JcaSigner(applicationConfiguration.signer)

  val cookieAuthenticator =
    new CookieAuthenticator(signer, crypter, applicationConfiguration.cookie, userRepository)
  val apiKeyAuthenticator = new APIKeyAuthenticator(passwordHasherRegistry, consumerRepository)

  val credentialsProvider = new CredentialsProvider(passwordHasherRegistry, userRepository)

  implicit val bucketConfiguration: BucketConfiguration = BucketConfiguration(
    keyId = configuration.get[String]("pekko.connectors.s3.aws.credentials.access-key-id"),
    secretKey = configuration.get[String]("pekko.connectors.s3.aws.credentials.secret-access-key"),
    amazonBucketName = applicationConfiguration.amazonBucketName
  )

  private val awsS3Client: AmazonS3 = AmazonS3ClientBuilder
    .standard()
    .withEndpointConfiguration(
      new EndpointConfiguration("https://cellar-c2.services.clever-cloud.com", "us-east-1")
    )
    .withCredentials(
      new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(
          bucketConfiguration.keyId,
          bucketConfiguration.secretKey
        )
      )
    )
    .build()
  lazy val s3Service: S3ServiceInterface = new S3Service(awsS3Client)

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
    actorSystem.spawn(HtmlConverterActor.create(), "html-converter-actor")

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

  // Orchestrator

  val userOrchestrator = new UserOrchestrator(userRepository, eventRepository)

  val proAccessTokenOrchestrator = new ProAccessTokenOrchestrator(
    userOrchestrator,
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
    credentialsProvider,
    mailService,
    cookieAuthenticator
  )

  def companiesVisibilityOrchestrator =
    new CompaniesVisibilityOrchestrator(companyRepository, companyAccessRepository)

  val companyAccessOrchestrator =
    new CompanyAccessOrchestrator(
      companyAccessRepository,
      companyRepository,
      accessTokenRepository,
      companyActivationAttemptRepository,
      proAccessTokenOrchestrator
    )

  private val taskConfiguration: TaskConfiguration = applicationConfiguration.task
  val companyOrchestrator = new CompanyOrchestrator(
    companyRepository,
    companiesVisibilityOrchestrator,
    reportRepository,
    websiteRepository,
    accessTokenRepository,
    eventRepository,
    pdfService,
    taskConfiguration,
    frontRoute,
    emailConfiguration,
    tokenConfiguration
  )

  val dataEconomieOrchestrator = new DataEconomieOrchestrator(reportRepository)
  val emailValidationOrchestrator =
    new EmailValidationOrchestrator(mailService, emailValidationRepository, emailConfiguration, messagesApi)

  val eventsOrchestrator = new EventsOrchestrator(eventRepository, reportRepository, companyRepository)

  val reportBlockedNotificationOrchestrator = new ReportBlockedNotificationOrchestrator(
    reportNotificationBlockedRepository
  )

  val reportConsumerReviewOrchestrator =
    new ReportConsumerReviewOrchestrator(
      reportRepository,
      eventRepository,
      responseConsumerReviewRepository
    )

  val htmlFromTemplateGenerator = new HtmlFromTemplateGenerator(messagesApi, frontRoute)

  val reportZipExportService =
    new ReportZipExportService(htmlFromTemplateGenerator, pdfService, s3Service)(materializer, actorSystem)

  def antivirusService: AntivirusServiceInterface =
    new AntivirusService(conf = signalConsoConfiguration.antivirusServiceConfiguration, backend)

  val reportFileOrchestrator =
    new ReportFileOrchestrator(
      reportFileRepository,
      antivirusScanActor,
      s3Service,
      reportZipExportService,
      antivirusService
    )

  val engagementOrchestrator =
    new EngagementOrchestrator(
      engagementRepository,
      companiesVisibilityOrchestrator,
      eventRepository,
      reportRepository,
      reportEngagementReviewRepository
    )

  val bookmarkOrchestrator = new BookmarkOrchestrator(reportRepository, bookmarkRepository)

  val emailNotificationOrchestrator = new EmailNotificationOrchestrator(mailService, subscriptionRepository)

  private def buildReportOrchestrator(emailService: MailServiceInterface) = new ReportOrchestrator(
    emailService,
    reportConsumerReviewOrchestrator,
    reportRepository,
    reportMetadataRepository,
    reportFileOrchestrator,
    companyRepository,
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
    engagementOrchestrator,
    messagesApi
  )

  val reportOrchestrator = buildReportOrchestrator(mailService)

  val reportAssignmentOrchestrator = new ReportAssignmentOrchestrator(
    reportOrchestrator,
    companiesVisibilityOrchestrator,
    mailService,
    reportMetadataRepository,
    userRepository,
    eventRepository
  )

  val reportWithDataOrchestrator =
    new ReportWithDataOrchestrator(
      reportOrchestrator,
      companyRepository,
      eventRepository,
      reportFileRepository,
      responseConsumerReviewRepository,
      reportEngagementReviewRepository
    )

  val socialBladeClient      = new SocialBladeClient(applicationConfiguration.socialBlade)
  val influencerOrchestrator = new InfluencerOrchestrator(influencerRepository, socialBladeClient)

  val reportsExtractActor: typed.ActorRef[ReportsExtractActor.ReportsExtractCommand] =
    actorSystem.spawn(
      ReportsExtractActor.create(
        reportConsumerReviewOrchestrator,
        engagementOrchestrator,
        reportFileRepository,
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
    new WebsitesOrchestrator(websiteRepository, companyRepository, reportRepository, reportOrchestrator)

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
    websiteRepository,
    eventRepository,
    engagementRepository
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
    companyAccessRepository,
    eventRepository,
    mailService,
    pdfService,
    emailConfiguration,
    taskConfiguration,
    reportFileOrchestrator,
    companyRepository,
    emailNotificationOrchestrator,
    ipBlackListRepository,
    albertClassificationRepository,
    albertService,
    sampleDataService,
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
      proAccessTokenOrchestrator,
      companiesVisibilityOrchestrator,
      companyAccessOrchestrator,
      eventRepository,
      cookieAuthenticator,
      controllerComponents
    )

  val companyController = new CompanyController(
    companyOrchestrator,
    companiesVisibilityOrchestrator,
    companyRepository,
    albertOrchestrator,
    cookieAuthenticator,
    controllerComponents
  )

  val constantController  = new ConstantController(cookieAuthenticator, controllerComponents)
  val mobileAppController = new MobileAppController(signalConsoConfiguration, cookieAuthenticator, controllerComponents)
  val dataEconomieController =
    new DataEconomieController(dataEconomieOrchestrator, apiKeyAuthenticator, controllerComponents)
  val emailValidationController =
    new EmailValidationController(cookieAuthenticator, emailValidationOrchestrator, controllerComponents)

  val eventsController = new EventsController(eventsOrchestrator, cookieAuthenticator, controllerComponents)
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
      cookieAuthenticator,
      signalConsoConfiguration,
      controllerComponents,
      reportRepository
    )

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
    reportZipExportService,
    htmlFromTemplateGenerator
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
      cookieAuthenticator,
      controllerComponents
    )

  val signalConsoReviewController =
    new SignalConsoReviewController(signalConsoReviewRepository, cookieAuthenticator, controllerComponents)

  val reportToExternalController =
    new ReportToExternalController(
      reportRepository,
      reportFileRepository,
      reportOrchestrator,
      apiKeyAuthenticator,
      signalConsoConfiguration,
      controllerComponents
    )

  val staticController = new StaticController(cookieAuthenticator, controllerComponents)

  val statisticController = new StatisticController(statsOrchestrator, cookieAuthenticator, controllerComponents)

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

  val siretExtractorService = new SiretExtractorService(applicationConfiguration.siretExtractor)
  val siretExtractorController =
    new SiretExtractorController(siretExtractorService, cookieAuthenticator, controllerComponents)

  val importOrchestrator = new ImportOrchestrator(
    companyRepository,
    companySyncService,
    userOrchestrator,
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
      new HealthController(controllerComponents),
      staticController,
      statisticController,
      companyAccessController,
      reportListController,
      reportFileController,
      reportController,
      reportConsumerReviewController,
      eventsController,
      bookmarkController,
      reportToExternalController,
      dataEconomieController,
      adminController,
      asyncFileController,
      constantController,
      socialNetworkController,
      mobileAppController,
      authController,
      accountController,
      emailValidationController,
      companyController,
      ratingController,
      subscriptionController,
      websiteController,
      reportedPhoneController,
      reportBlockedNotificationController,
      blacklistedEmailsController,
      signalConsoReviewController,
      siretExtractorController,
      importController,
      barcodeController,
      engagementController,
      assets
    )

  def scheduleTasks() = {
    companyUpdateTask.schedule()
    reportNotificationTask.schedule()
    inactiveAccountTask.schedule()
    engagementEmailTask.schedule()
    exportReportsToSFTPTask.schedule()
    reportClosureTask.schedule()
    reportReminderTask.schedule()
    orphanReportFileDeletionTask.schedule()
    oldReportExportDeletionTask.schedule()
    oldReportsRgpdDeletionTask.schedule()
    if (applicationConfiguration.task.probe.active) {
      probeOrchestrator.scheduleProbeTasks()
    }
    if (applicationConfiguration.task.sampleData.active) {
      sampleDataGenerationTask.schedule()
    }
    subcategoryLabelTask.schedule()
    companyAlbertLabelTask.schedule()
    companyReportCountViewRefresherTask.schedule()
  }

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
