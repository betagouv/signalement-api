package loader

import _root_.controllers._
import actors.ReportedPhonesExtractActor.ReportedPhonesExtractCommand
import actors._
import org.apache.pekko.actor.typed
import org.apache.pekko.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import org.apache.pekko.util.Timeout
import authentication._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config._
import models.report.ArborescenceNode
import orchestrators._
import orchestrators.socialmedia.InfluencerOrchestrator
import orchestrators.socialmedia.SocialBladeClient
import org.flywaydb.core.Flyway
import play.api._
import play.api.db.slick.DbName
import play.api.db.slick.SlickComponents
import play.api.libs.json.JsArray
import play.api.libs.json.Json
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
import repositories.asyncfiles.AsyncFileRepository
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.authattempt.AuthAttemptRepository
import repositories.authattempt.AuthAttemptRepositoryInterface
import repositories.authtoken.AuthTokenRepository
import repositories.authtoken.AuthTokenRepositoryInterface
import repositories.barcode.BarcodeProductRepository
import repositories.blacklistedemails.BlacklistedEmailsRepository
import repositories.blacklistedemails.BlacklistedEmailsRepositoryInterface
import repositories.company.CompanyRepository
import repositories.company.CompanyRepositoryInterface
import repositories.company.CompanySyncRepository
import repositories.company.CompanySyncRepositoryInterface
import repositories.companyaccess.CompanyAccessRepository
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.companyactivationattempt.CompanyActivationAttemptRepository
import repositories.companyactivationattempt.CompanyActivationAttemptRepositoryInterface
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
import repositories.subscription.SubscriptionRepository
import repositories.subscription.SubscriptionRepositoryInterface
import repositories.tasklock.TaskRepository
import repositories.user.UserRepository
import repositories.user.UserRepositoryInterface
import repositories.usersettings.UserReportsFiltersRepository
import repositories.usersettings.UserReportsFiltersRepositoryInterface
import repositories.website.WebsiteRepository
import repositories.website.WebsiteRepositoryInterface
import services._
import services.emails.MailRetriesService
import services.emails.MailService
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import tasks.EngagementEmailTask
import tasks.ExportReportsToSFTPTask
import tasks.account.InactiveAccountTask
import tasks.account.InactiveDgccrfAccountReminderTask
import tasks.account.InactiveDgccrfAccountRemoveTask
import tasks.company._
import tasks.probe.LanceurDAlerteRateProbeTask
import tasks.probe.ReponseConsoRateProbeTask
import tasks.report.ReportClosureTask
import tasks.report.ReportNotificationTask
import tasks.report.ReportRemindersTask
import utils.CustomIpFilter
import utils.EmailAddress
import utils.FrontRoute
import utils.LoggingFilter

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.annotation.nowarn
import scala.concurrent.duration.DurationInt

class SignalConsoApplicationLoader() extends ApplicationLoader {
  var components: SignalConsoComponents = _

  override def load(context: ApplicationLoader.Context): Application = {
    components = new SignalConsoComponents(context)
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
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
  val asyncFileRepository: AsyncFileRepositoryInterface     = new AsyncFileRepository(dbConfig)
  val authAttemptRepository: AuthAttemptRepositoryInterface = new AuthAttemptRepository(dbConfig)
  val authTokenRepository: AuthTokenRepositoryInterface     = new AuthTokenRepository(dbConfig)
  def companyRepository: CompanyRepositoryInterface         = new CompanyRepository(dbConfig)
  val companyActivationAttemptRepository: CompanyActivationAttemptRepositoryInterface =
    new CompanyActivationAttemptRepository(dbConfig)
  val consumerRepository: ConsumerRepositoryInterface               = new ConsumerRepository(dbConfig)
  val emailValidationRepository: EmailValidationRepositoryInterface = new EmailValidationRepository(dbConfig)

  def eventRepository: EventRepositoryInterface                   = new EventRepository(dbConfig)
  val ratingRepository: RatingRepositoryInterface                 = new RatingRepository(dbConfig)
  val influencerRepository: InfluencerRepositoryInterface         = new InfluencerRepository(dbConfig)
  def reportRepository: ReportRepositoryInterface                 = new ReportRepository(dbConfig)
  val reportMetadataRepository: ReportMetadataRepositoryInterface = new ReportMetadataRepository(dbConfig)
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

  val ipBlackListRepository = new IpBlackListRepository(dbConfig)

  val crypter              = new JcaCrypter(applicationConfiguration.crypter)
  val signer               = new JcaSigner(applicationConfiguration.signer)
  val fingerprintGenerator = new FingerprintGenerator()

  val cookieAuthenticator =
    new CookieAuthenticator(signer, crypter, fingerprintGenerator, applicationConfiguration.cookie, userRepository)
  val apiKeyAuthenticator = new APIKeyAuthenticator(passwordHasherRegistry, consumerRepository)

  val credentialsProvider = new CredentialsProvider(passwordHasherRegistry, userRepository)

  implicit val bucketConfiguration: BucketConfiguration = BucketConfiguration(
    keyId = configuration.get[String]("pekko.connectors.s3.aws.credentials.access-key-id"),
    secretKey = configuration.get[String]("pekko.connectors.s3.aws.credentials.secret-access-key"),
    amazonBucketName = applicationConfiguration.amazonBucketName
  )

  def s3Service: S3ServiceInterface = new S3Service()

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
  val mailService = new MailService(
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

  val reportFileOrchestrator =
    new ReportFileOrchestrator(reportFileRepository, antivirusScanActor, s3Service, reportZipExportService)

  val engagementOrchestrator =
    new EngagementOrchestrator(
      engagementRepository,
      companiesVisibilityOrchestrator,
      eventRepository,
      reportRepository,
      reportEngagementReviewRepository
    )
  val reportOrchestrator = new ReportOrchestrator(
    mailService,
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
    subscriptionRepository,
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

  // This file can be generated in the website using 'yarn minimized-anomalies'.
  // This is the first iteration of the story, using an copied generated file from the website
  // The second version will be to expose the file in the website and fetch it in the API at runtime.
  val arborescenceFrAsJson = context.environment
    .resourceAsStream("minimized-anomalies_fr.json")
    .map(json =>
      try Json.parse(json)
      finally json.close()
    )
    .map(_.as[JsArray])
    .map(ArborescenceNode.fromJson)
    .get

  val arborescenceEnAsJson = context.environment
    .resourceAsStream("minimized-anomalies_en.json")
    .map(json =>
      try Json.parse(json)
      finally json.close()
    )
    .map(_.as[JsArray])
    .map(ArborescenceNode.fromJson)
    .get

  val statsOrchestrator =
    new StatsOrchestrator(
      reportRepository,
      eventRepository,
      responseConsumerReviewRepository,
      reportEngagementReviewRepository,
      accessTokenRepository,
      arborescenceFrAsJson,
      arborescenceEnAsJson
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
    messagesApi
  )

  val probeOrchestrator = new ProbeOrchestrator(userRepository, mailService)

  val websitesOrchestrator =
    new WebsitesOrchestrator(websiteRepository, companyRepository, reportRepository, reportOrchestrator)

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
  reportClosureTask.schedule()

  val reportReminderTask = new ReportRemindersTask(
    actorSystem,
    reportRepository,
    eventRepository,
    mailService,
    companiesVisibilityOrchestrator,
    taskConfiguration,
    taskRepository
  )
  reportReminderTask.schedule()

  def companySyncService: CompanySyncServiceInterface = new CompanySyncService(
    applicationConfiguration.task.companyUpdate
  )

  val companySyncRepository: CompanySyncRepositoryInterface = new CompanySyncRepository(dbConfig)
  val companyUpdateTask = new CompanyUpdateTask(
    actorSystem,
    companyRepository,
    companySyncService,
    companySyncRepository,
    taskConfiguration,
    taskRepository
  )
  companyUpdateTask.schedule()

  logger.debug("Starting App and sending sentry alert")

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
  reportNotificationTask.schedule()

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
  inactiveAccountTask.schedule()

  val engagementEmailTask = new EngagementEmailTask(
    mailService,
    companyRepository,
    engagementRepository,
    actorSystem,
    taskConfiguration,
    taskRepository,
    messagesApi
  )
  engagementEmailTask.schedule()

  val test = new ExportReportsToSFTPTask(
    actorSystem,
    taskConfiguration,
    taskRepository,
    reportRepository
  )

  test.schedule()

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
    reportFileOrchestrator,
    companyRepository,
    subscriptionRepository,
    frontRoute,
    cookieAuthenticator,
    controllerComponents
  )

  val socialNetworkController =
    new SocialNetworkController(influencerOrchestrator, cookieAuthenticator, controllerComponents)
  val asyncFileController =
    new AsyncFileController(asyncFileRepository, s3Service, cookieAuthenticator, controllerComponents)

  val authController = new AuthController(
    authOrchestrator,
    cookieAuthenticator,
    controllerComponents,
    applicationConfiguration.app.enableRateLimit
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
      cookieAuthenticator,
      controllerComponents
    )

  val companyController = new CompanyController(
    companyOrchestrator,
    companiesVisibilityOrchestrator,
    companyRepository,
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

  val userReportsFiltersRepository: UserReportsFiltersRepositoryInterface = new UserReportsFiltersRepository(dbConfig)
  val userReportsFiltersOrchestrator = new UserReportsFiltersOrchestrator(userReportsFiltersRepository)
  val userReportsFiltersController = new UserReportsFiltersController(
    userReportsFiltersOrchestrator,
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
    proAccessTokenOrchestrator
  )
  val importController = new ImportController(
    importOrchestrator,
    cookieAuthenticator,
    controllerComponents
  )

  val openFoodFactsService     = new OpenFoodFactsService
  val openBeautyFactsService   = new OpenBeautyFactsService
  val barcodeProductRepository = new BarcodeProductRepository(dbConfig)
  val gs1Service               = new GS1Service(applicationConfiguration.gs1)
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
  val barcodeController = new BarcodeController(barcodeOrchestrator, cookieAuthenticator, controllerComponents)

  val engagementController =
    new EngagementController(engagementOrchestrator, cookieAuthenticator, controllerComponents)

  io.sentry.Sentry.captureException(
    new Exception("This is a test Alert, used to check that Sentry alert are still active on each new deployments.")
  )

  // Probes
  if (applicationConfiguration.task.probe.active) {
    logger.debug("Probes are enabled")
    val probeRepository = new ProbeRepository(dbConfig)
    new ReponseConsoRateProbeTask(
      actorSystem,
      applicationConfiguration.task,
      probeOrchestrator,
      taskRepository,
      probeRepository
    ).schedule()
    new LanceurDAlerteRateProbeTask(
      actorSystem,
      applicationConfiguration.task,
      probeOrchestrator,
      taskRepository,
      probeRepository
    ).schedule()
  } else {
    logger.debug("Probes are disabled")
  }

  // Routes
  lazy val router: Router =
    new _root_.router.Routes(
      httpErrorHandler,
      staticController,
      statisticController,
      companyAccessController,
      reportListController,
      reportFileController,
      reportController,
      reportConsumerReviewController,
      eventsController,
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
      userReportsFiltersController,
      signalConsoReviewController,
      siretExtractorController,
      importController,
      barcodeController,
      engagementController,
      assets
    )

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
