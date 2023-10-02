package loader

import _root_.controllers._
import actors._
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.typed
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.SilhouetteProvider
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticatorService
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config._
import models.report.ArborescenceNode
import orchestrators._
import org.flywaydb.core.Flyway
import play.api._
import play.api.db.slick.DbName
import play.api.db.slick.SlickComponents
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.mailer.MailerComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.BodyParsers
import play.api.mvc.EssentialFilter
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
import repositories.dataeconomie.DataEconomieRepository
import repositories.dataeconomie.DataEconomieRepositoryInterface
import repositories.emailvalidation.EmailValidationRepository
import repositories.emailvalidation.EmailValidationRepositoryInterface
import repositories.event.EventRepository
import repositories.event.EventRepositoryInterface
import repositories.rating.RatingRepository
import repositories.rating.RatingRepositoryInterface
import repositories.report.ReportRepository
import repositories.report.ReportRepositoryInterface
import repositories.reportblockednotification.ReportNotificationBlockedRepository
import repositories.reportblockednotification.ReportNotificationBlockedRepositoryInterface
import repositories.reportconsumerreview.ResponseConsumerReviewRepository
import repositories.reportconsumerreview.ResponseConsumerReviewRepositoryInterface
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
import repositories.user.UserRepository
import repositories.user.UserRepositoryInterface
import repositories.usersettings.UserReportsFiltersRepository
import repositories.usersettings.UserReportsFiltersRepositoryInterface
import repositories.website.WebsiteRepository
import repositories.website.WebsiteRepositoryInterface
import services._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import tasks.account.InactiveAccountTask
import tasks.account.InactiveDgccrfAccountReminderTask
import tasks.account.InactiveDgccrfAccountRemoveTask
import tasks.company._
import tasks.report.ReportClosureTask
import tasks.report.ReportNotificationTask
import tasks.report.ReportRemindersTask
import utils.EmailAddress
import utils.FrontRoute
import utils.LoggingFilter
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.api.APIKeyRequestProvider
import utils.silhouette.api.ApiKeyService
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.PasswordInfoDAO
import utils.silhouette.auth.PasswordInfoIncludingDeletedDAO
import utils.silhouette.auth.UserService

import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    with SecuredActionComponents
    with SecuredErrorHandlerComponents
    with UnsecuredActionComponents
    with UnsecuredErrorHandlerComponents
    with UserAwareActionComponents
    with MailerComponents {

  val logger: Logger = Logger(this.getClass)

  implicit val localTimeInstance: ConfigConvert[LocalTime] = localTimeConfigConvert(DateTimeFormatter.ISO_TIME)
  implicit val personReader: ConfigReader[EmailAddress] = deriveReader[EmailAddress]
  val csvStringListReader = ConfigReader[String].map(_.split(",").toList)
  implicit val stringListReader = ConfigReader[List[String]].orElse(csvStringListReader)

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

  def emailConfiguration = applicationConfiguration.mail
  def signalConsoConfiguration: SignalConsoConfiguration = applicationConfiguration.app
  def tokenConfiguration = signalConsoConfiguration.token
  def uploadConfiguration: UploadConfiguration = signalConsoConfiguration.upload

  def mobileAppConfiguration = signalConsoConfiguration.mobileApp

  def passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(
    new BCryptPasswordHasher()
  )

  //  Repositories

  val dbConfig: DatabaseConfig[JdbcProfile] = slickApi.dbConfig[JdbcProfile](DbName("default"))

  val blacklistedEmailsRepository: BlacklistedEmailsRepositoryInterface = new BlacklistedEmailsRepository(dbConfig)
  val companyAccessRepository: CompanyAccessRepositoryInterface = new CompanyAccessRepository(dbConfig)
  val accessTokenRepository: AccessTokenRepositoryInterface =
    new AccessTokenRepository(dbConfig, companyAccessRepository)
  val asyncFileRepository: AsyncFileRepositoryInterface = new AsyncFileRepository(dbConfig)
  val authAttemptRepository: AuthAttemptRepositoryInterface = new AuthAttemptRepository(dbConfig)
  val authTokenRepository: AuthTokenRepositoryInterface = new AuthTokenRepository(dbConfig)
  def companyRepository: CompanyRepositoryInterface = new CompanyRepository(dbConfig)
  val companyActivationAttemptRepository: CompanyActivationAttemptRepositoryInterface =
    new CompanyActivationAttemptRepository(dbConfig)
  val consumerRepository: ConsumerRepositoryInterface = new ConsumerRepository(dbConfig)
  val dataEconomieRepository: DataEconomieRepositoryInterface = new DataEconomieRepository(actorSystem)
  val emailValidationRepository: EmailValidationRepositoryInterface = new EmailValidationRepository(dbConfig)

  def eventRepository: EventRepositoryInterface = new EventRepository(dbConfig)
  val ratingRepository: RatingRepositoryInterface = new RatingRepository(dbConfig)
  def reportRepository: ReportRepositoryInterface = new ReportRepository(dbConfig)
  val reportMetadataRepository: ReportMetadataRepositoryInterface = new ReportMetadataRepository(dbConfig)
  val reportNotificationBlockedRepository: ReportNotificationBlockedRepositoryInterface =
    new ReportNotificationBlockedRepository(dbConfig)
  val responseConsumerReviewRepository: ResponseConsumerReviewRepositoryInterface =
    new ResponseConsumerReviewRepository(dbConfig)
  def reportFileRepository: ReportFileRepositoryInterface = new ReportFileRepository(dbConfig)
  val subscriptionRepository: SubscriptionRepositoryInterface = new SubscriptionRepository(dbConfig)
  val userRepository: UserRepositoryInterface = new UserRepository(dbConfig, passwordHasherRegistry)
  val websiteRepository: WebsiteRepositoryInterface = new WebsiteRepository(dbConfig)
  val socialNetworkRepository: SocialNetworkRepositoryInterface = new SocialNetworkRepository(dbConfig)

  val signalConsoReviewRepository: SignalConsoReviewRepositoryInterface = new SignalConsoReviewRepository(dbConfig)

  val userService = new UserService(userRepository)
  val apiUserService = new ApiKeyService(consumerRepository)

  val authenticatorService: AuthenticatorService[CookieAuthenticator] =
    SilhouetteEnv.getCookieAuthenticatorService(configuration)

  def authEnv: Environment[AuthEnv] = SilhouetteEnv.getEnv[AuthEnv](userService, authenticatorService)

  val silhouette: Silhouette[AuthEnv] =
    new SilhouetteProvider[AuthEnv](authEnv, securedAction, unsecuredAction, userAwareAction)

  def authApiEnv: Environment[APIKeyEnv] =
    SilhouetteEnv.getEnv[APIKeyEnv](
      apiUserService,
      new DummyAuthenticatorService(),
      Seq(new APIKeyRequestProvider(passwordHasherRegistry, consumerRepository))
    )

  val silhouetteApi: Silhouette[APIKeyEnv] =
    new SilhouetteProvider[APIKeyEnv](authApiEnv, securedAction, unsecuredAction, userAwareAction)

  val authInfoRepository = new DelegableAuthInfoRepository(
    new PasswordInfoDAO(
      userRepository
    )
  )

  val authInfoIncludingDeletedUsersRepository = new DelegableAuthInfoRepository(
    new PasswordInfoIncludingDeletedDAO(
      userRepository
    )
  )

  val credentialsProvider = new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  val credentialsProviderIncludingDeletedUsers =
    new CredentialsProvider(authInfoIncludingDeletedUsersRepository, passwordHasherRegistry)

  implicit val bucketConfiguration: BucketConfiguration = BucketConfiguration(
    keyId = configuration.get[String]("alpakka.s3.aws.credentials.access-key-id"),
    secretKey = configuration.get[String]("alpakka.s3.aws.credentials.secret-access-key"),
    amazonBucketName = applicationConfiguration.amazonBucketName
  )

  def s3Service: S3ServiceInterface = new S3Service()

  //  Actor
  val antivirusScanActor: typed.ActorRef[AntivirusScanActor.ScanCommand] = actorSystem.spawn(
    AntivirusScanActor.create(uploadConfiguration, reportFileRepository, s3Service),
    "antivirus-scan-actor"
  )
  val reportedPhonesExtractActor: ActorRef =
    actorSystem.actorOf(
      Props(
        new ReportedPhonesExtractActor(signalConsoConfiguration, reportRepository, asyncFileRepository, s3Service)
      ),
      "reported-phones-extract-actor"
    )

  val websitesExtractActor: ActorRef =
    actorSystem.actorOf(
      Props(new WebsitesExtractActor(websiteRepository, asyncFileRepository, s3Service, signalConsoConfiguration)),
      "websites-extract-actor"
    )

  val htmlConverterActor: typed.ActorRef[HtmlConverterActor.ConvertCommand] =
    actorSystem.spawn(HtmlConverterActor.create(), "html-converter-actor")

  val pdfService = new PDFService(signalConsoConfiguration, htmlConverterActor)
  implicit val frontRoute = new FrontRoute(signalConsoConfiguration)
  val attachmentService = new AttachmentService(environment, pdfService, frontRoute)
  lazy val mailRetriesService = new MailRetriesService(mailerClient, executionContext, actorSystem)
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
    userService,
    authAttemptRepository,
    userRepository,
    accessesOrchestrator,
    authTokenRepository,
    tokenConfiguration,
    credentialsProvider,
    credentialsProviderIncludingDeletedUsers,
    mailService,
    silhouette
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

  val dataEconomieOrchestrator = new DataEconomieOrchestrator(dataEconomieRepository)
  val emailValidationOrchestrator =
    new EmailValidationOrchestrator(mailService, emailValidationRepository, emailConfiguration, messagesApi)

  val eventsOrchestrator = new EventsOrchestrator(eventRepository, reportRepository, companyRepository)

  val reportBlockedNotificationOrchestrator = new ReportBlockedNotificationOrchestrator(
    reportNotificationBlockedRepository
  )

  val reportConsumerReviewOrchestrator =
    new ReportConsumerReviewOrchestrator(reportRepository, eventRepository, responseConsumerReviewRepository)

  val reportFileOrchestrator = new ReportFileOrchestrator(reportFileRepository, antivirusScanActor, s3Service)

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
    websiteRepository,
    companiesVisibilityOrchestrator,
    subscriptionRepository,
    blacklistedEmailsRepository,
    emailValidationOrchestrator,
    emailConfiguration,
    tokenConfiguration,
    signalConsoConfiguration,
    companySyncService,
    messagesApi
  )

  val reportsExtractActor: ActorRef =
    actorSystem.actorOf(
      Props(
        new ReportsExtractActor(
          reportConsumerReviewOrchestrator,
          reportFileRepository,
          companyAccessRepository,
          reportOrchestrator,
          eventRepository,
          asyncFileRepository,
          s3Service,
          signalConsoConfiguration
        )
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
      accessTokenRepository,
      arborescenceFrAsJson,
      arborescenceEnAsJson
    )

  val websitesOrchestrator =
    new WebsitesOrchestrator(websiteRepository, companyRepository, reportRepository, reportOrchestrator)

  val reportClosureTask = new ReportClosureTask(
    actorSystem,
    reportRepository,
    eventRepository,
    companyRepository,
    mailService,
    taskConfiguration,
    messagesApi
  )
  val reportReminderTask = new ReportRemindersTask(
    reportRepository,
    eventRepository,
    mailService,
    companiesVisibilityOrchestrator
  )
  reportReminderTask.schedule(actorSystem, taskConfiguration)

  def companySyncService: CompanySyncServiceInterface = new CompanySyncService(
    applicationConfiguration.task.companyUpdate
  )

  val companySyncRepository: CompanySyncRepositoryInterface = new CompanySyncRepository(dbConfig)
  val companyUpdateTask = new CompanyUpdateTask(
    actorSystem,
    companyRepository,
    companySyncService,
    companySyncRepository
  )
  companyUpdateTask.schedule()

  logger.debug("Starting App and sending sentry alert")

  val reportNotificationTask =
    new ReportNotificationTask(actorSystem, reportRepository, subscriptionRepository, mailService, taskConfiguration)

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
    applicationConfiguration.task
  )

  // Controller

  val blacklistedEmailsController =
    new BlacklistedEmailsController(blacklistedEmailsRepository, silhouette, controllerComponents)

  val accountController = new AccountController(
    silhouette,
    userOrchestrator,
    userRepository,
    accessesOrchestrator,
    proAccessTokenOrchestrator,
    emailConfiguration,
    controllerComponents
  )

  val adminController = new AdminController(
    silhouette,
    reportRepository,
    companyAccessRepository,
    eventRepository,
    mailService,
    pdfService,
    emailConfiguration,
    reportFileOrchestrator,
    companyRepository,
    frontRoute,
    controllerComponents
  )

  val asyncFileController = new AsyncFileController(asyncFileRepository, silhouette, s3Service, controllerComponents)

  val authController = new AuthController(silhouette, authOrchestrator, controllerComponents)

  val companyAccessController =
    new CompanyAccessController(
      userRepository,
      companyRepository,
      companyAccessRepository,
      accessTokenRepository,
      proAccessTokenOrchestrator,
      companiesVisibilityOrchestrator,
      companyAccessOrchestrator,
      silhouette,
      controllerComponents
    )

  val companyController = new CompanyController(
    companyOrchestrator,
    companiesVisibilityOrchestrator,
    companyRepository,
    accessTokenRepository,
    eventRepository,
    reportRepository,
    silhouette,
    companiesVisibilityOrchestrator,
    taskConfiguration,
    controllerComponents
  )

  val constantController = new ConstantController(silhouette, controllerComponents)
  val mobileAppController = new MobileAppController(signalConsoConfiguration, silhouette, controllerComponents)
  val dataEconomieController = new DataEconomieController(dataEconomieOrchestrator, silhouetteApi, controllerComponents)
  val emailValidationController =
    new EmailValidationController(silhouette, emailValidationOrchestrator, controllerComponents)

  val eventsController = new EventsController(eventsOrchestrator, silhouette, controllerComponents)
  val ratingController = new RatingController(ratingRepository, silhouette, controllerComponents)
  val reportBlockedNotificationController =
    new ReportBlockedNotificationController(
      silhouette,
      silhouetteApi,
      reportBlockedNotificationOrchestrator,
      controllerComponents
    )
  val reportConsumerReviewController =
    new ReportConsumerReviewController(reportConsumerReviewOrchestrator, silhouette, controllerComponents)

  val reportFileController =
    new ReportFileController(reportFileOrchestrator, silhouette, signalConsoConfiguration, controllerComponents)

  val reportWithDataOrchestrator =
    new ReportWithDataOrchestrator(
      reportOrchestrator,
      companyRepository,
      eventRepository,
      reportFileRepository,
      responseConsumerReviewRepository
    )

  val reportController = new ReportController(
    reportOrchestrator,
    eventsOrchestrator,
    reportRepository,
    reportFileRepository,
    companyRepository,
    pdfService,
    frontRoute,
    silhouette,
    controllerComponents,
    reportWithDataOrchestrator
  )
  val reportedPhoneController = new ReportedPhoneController(
    reportRepository,
    companyRepository,
    asyncFileRepository,
    reportedPhonesExtractActor,
    silhouette,
    controllerComponents
  )

  val reportListController =
    new ReportListController(
      reportOrchestrator,
      asyncFileRepository,
      reportsExtractActor,
      silhouette,
      silhouetteApi,
      controllerComponents
    )

  val signalConsoReviewController =
    new SignalConsoReviewController(signalConsoReviewRepository, silhouette, controllerComponents)

  val reportToExternalController =
    new ReportToExternalController(
      reportRepository,
      reportFileRepository,
      reportOrchestrator,
      silhouetteApi,
      controllerComponents
    )

  val staticController = new StaticController(silhouette, controllerComponents)

  val statisticController = new StatisticController(statsOrchestrator, silhouette, controllerComponents)

  val subscriptionController = new SubscriptionController(subscriptionRepository, silhouette, controllerComponents)
  val websiteController = new WebsiteController(
    websitesOrchestrator,
    companyRepository,
    websitesExtractActor,
    silhouette,
    controllerComponents
  )

  val userReportsFiltersRepository: UserReportsFiltersRepositoryInterface = new UserReportsFiltersRepository(dbConfig)
  val userReportsFiltersOrchestrator = new UserReportsFiltersOrchestrator(userReportsFiltersRepository)
  val userReportsFiltersController = new UserReportsFiltersController(
    userReportsFiltersOrchestrator,
    silhouette,
    controllerComponents
  )

  val siretExtractorService = new SiretExtractorService(applicationConfiguration.siretExtractor)
  val siretExtractorController = new SiretExtractorController(siretExtractorService, silhouette, controllerComponents)

  val importOrchestrator = new ImportOrchestrator(
    companyRepository,
    companySyncService,
    userOrchestrator,
    proAccessTokenOrchestrator
  )
  val importController = new ImportController(
    importOrchestrator,
    silhouette,
    controllerComponents
  )

  io.sentry.Sentry.captureException(
    new Exception("This is a test Alert, used to check that Sentry alert are still active on each new deployments.")
  )

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
      assets
    )

  override def securedBodyParser: BodyParsers.Default = new BodyParsers.Default(controllerComponents.parsers)

  override def unsecuredBodyParser: BodyParsers.Default = new BodyParsers.Default(controllerComponents.parsers)

  override def userAwareBodyParser: BodyParsers.Default = new BodyParsers.Default(controllerComponents.parsers)

  override def config: Config = ConfigFactory.load()

  override def httpFilters: Seq[EssentialFilter] =
    Seq(new LoggingFilter(), securityHeadersFilter, allowedHostsFilter, corsFilter)

}
