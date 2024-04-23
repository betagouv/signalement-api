package loader

import _root_.controllers._
import akka.util.Timeout
import authentication._
import com.typesafe.config.{Config, ConfigFactory}
import config._
import orchestrators._
import org.flywaydb.core.Flyway
import play.api._
import play.api.db.slick.{DbName, SlickComponents}
import play.api.libs.mailer.MailerComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{Cookie, EssentialFilter}
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import pureconfig.{ConfigConvert, ConfigReader, ConfigSource}
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import repositories.accesstoken.{AccessTokenRepository, AccessTokenRepositoryInterface}
import repositories.asyncfiles.{AsyncFileRepository, AsyncFileRepositoryInterface}
import repositories.authattempt.{AuthAttemptRepository, AuthAttemptRepositoryInterface}
import repositories.authtoken.{AuthTokenRepository, AuthTokenRepositoryInterface}
import repositories.blacklistedemails.{BlacklistedEmailsRepository, BlacklistedEmailsRepositoryInterface}
import repositories.company.{CompanyRepository, CompanyRepositoryInterface}
import repositories.companyaccess.{CompanyAccessRepository, CompanyAccessRepositoryInterface}
import repositories.companyactivationattempt.{CompanyActivationAttemptRepository, CompanyActivationAttemptRepositoryInterface}
import repositories.consumer.{ConsumerRepository, ConsumerRepositoryInterface}
import repositories.emailvalidation.{EmailValidationRepository, EmailValidationRepositoryInterface}
import repositories.event.{EventRepository, EventRepositoryInterface}
import repositories.influencer.{InfluencerRepository, InfluencerRepositoryInterface}
import repositories.report.{ReportRepository, ReportRepositoryInterface}
import repositories.reportblockednotification.{ReportNotificationBlockedRepository, ReportNotificationBlockedRepositoryInterface}
import repositories.reportconsumerreview.{ResponseConsumerReviewRepository, ResponseConsumerReviewRepositoryInterface}
import repositories.reportfile.{ReportFileRepository, ReportFileRepositoryInterface}
import repositories.reportmetadata.{ReportMetadataRepository, ReportMetadataRepositoryInterface}
import repositories.socialnetwork.{SocialNetworkRepository, SocialNetworkRepositoryInterface}
import repositories.subscription.{SubscriptionRepository, SubscriptionRepositoryInterface}
import repositories.tasklock.TaskRepository
import repositories.user.{UserRepository, UserRepositoryInterface}
import repositories.website.{WebsiteRepository, WebsiteRepositoryInterface}
import services._
import services.emails.{MailRetriesService, MailService}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import utils.{EmailAddress, FrontRoute, LoggingFilter}

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
  val influencerRepository: InfluencerRepositoryInterface         = new InfluencerRepository(dbConfig)
  def reportRepository: ReportRepositoryInterface                 = new ReportRepository(dbConfig)
  val reportMetadataRepository: ReportMetadataRepositoryInterface = new ReportMetadataRepository(dbConfig)
  val reportNotificationBlockedRepository: ReportNotificationBlockedRepositoryInterface =
    new ReportNotificationBlockedRepository(dbConfig)
  val responseConsumerReviewRepository: ResponseConsumerReviewRepositoryInterface =
    new ResponseConsumerReviewRepository(dbConfig)
  def reportFileRepository: ReportFileRepositoryInterface       = new ReportFileRepository(dbConfig)
  val subscriptionRepository: SubscriptionRepositoryInterface   = new SubscriptionRepository(dbConfig)
  def userRepository: UserRepositoryInterface                   = new UserRepository(dbConfig, passwordHasherRegistry)
  val websiteRepository: WebsiteRepositoryInterface             = new WebsiteRepository(dbConfig)
  val socialNetworkRepository: SocialNetworkRepositoryInterface = new SocialNetworkRepository(dbConfig)

  val crypter              = new JcaCrypter(applicationConfiguration.crypter)
  val signer               = new JcaSigner(applicationConfiguration.signer)
  val fingerprintGenerator = new FingerprintGenerator()

  val cookieAuthenticator =
    new CookieAuthenticator(signer, crypter, fingerprintGenerator, applicationConfiguration.cookie, userRepository)

  val credentialsProvider = new CredentialsProvider(passwordHasherRegistry, userRepository)

  implicit val bucketConfiguration: BucketConfiguration = BucketConfiguration(
    keyId = configuration.get[String]("alpakka.s3.aws.credentials.access-key-id"),
    secretKey = configuration.get[String]("alpakka.s3.aws.credentials.secret-access-key"),
    amazonBucketName = applicationConfiguration.amazonBucketName
  )

  def s3Service: S3ServiceInterface = new S3Service()

  //  Actor

  val pdfService                      = new PDFService(signalConsoConfiguration)
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

  val emailValidationOrchestrator =
    new EmailValidationOrchestrator(mailService, emailValidationRepository, emailConfiguration, messagesApi)

  val eventsOrchestrator = new EventsOrchestrator(eventRepository, reportRepository, companyRepository)

  val reportConsumerReviewOrchestrator =
    new ReportConsumerReviewOrchestrator(reportRepository, eventRepository, responseConsumerReviewRepository)

  val htmlFromTemplateGenerator = new HtmlFromTemplateGenerator(messagesApi, frontRoute)

  val reportZipExportService =
    new ReportZipExportService(htmlFromTemplateGenerator, pdfService, s3Service)(materializer, actorSystem)

  val reportFileOrchestrator =
    new ReportFileOrchestrator(reportFileRepository, s3Service, reportZipExportService)

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
      responseConsumerReviewRepository
    )


  logger.debug("Starting App and sending sentry alert")


  val reportListController =
    new ReportListController(
      reportOrchestrator,
      asyncFileRepository,
      cookieAuthenticator,
      controllerComponents
    )


  implicit val timeout: Timeout = 30.seconds

  io.sentry.Sentry.captureException(
    new Exception("This is a test Alert, used to check that Sentry alert are still active on each new deployments.")
  )

  // Routes
  lazy val router: Router =
    new _root_.router.Routes(
      httpErrorHandler,
      reportListController,
      assets
    )

  override def config: Config = ConfigFactory.load()

  override def httpFilters: Seq[EssentialFilter] =
    Seq(new LoggingFilter(), securityHeadersFilter, allowedHostsFilter, corsFilter)

}
