package loader

import _root_.controllers._
import akka.util.Timeout
import authentication._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config._
import orchestrators._
import play.api._
import play.api.db.slick.DbName
import play.api.db.slick.SlickComponents
import play.api.libs.mailer.MailerComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Cookie
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import pureconfig.ConfigConvert
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import repositories.consumer.ConsumerRepository
import repositories.consumer.ConsumerRepositoryInterface
import repositories.emailvalidation.EmailValidationRepository
import repositories.emailvalidation.EmailValidationRepositoryInterface
import repositories.event.EventRepository
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepository
import repositories.report.ReportRepositoryInterface
import repositories.reportconsumerreview.ResponseConsumerReviewRepository
import repositories.reportconsumerreview.ResponseConsumerReviewRepositoryInterface
import repositories.subscription.SubscriptionRepository
import repositories.subscription.SubscriptionRepositoryInterface
import repositories.tasklock.TaskRepository
import repositories.user.UserRepository
import repositories.user.UserRepositoryInterface
import services._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
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

  def signalConsoConfiguration: SignalConsoConfiguration = applicationConfiguration.app

  def passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(
    current = new BCryptPasswordHasher(),
    deprecated = Seq.empty
  )

  val dbConfig: DatabaseConfig[JdbcProfile] = slickApi.dbConfig[JdbcProfile](DbName("default"))

  val taskRepository = new TaskRepository(dbConfig)

  val consumerRepository: ConsumerRepositoryInterface               = new ConsumerRepository(dbConfig)
  val emailValidationRepository: EmailValidationRepositoryInterface = new EmailValidationRepository(dbConfig)

  def eventRepository: EventRepositoryInterface   = new EventRepository(dbConfig)
  def reportRepository: ReportRepositoryInterface = new ReportRepository(dbConfig)

  val responseConsumerReviewRepository: ResponseConsumerReviewRepositoryInterface =
    new ResponseConsumerReviewRepository(dbConfig)
  val subscriptionRepository: SubscriptionRepositoryInterface = new SubscriptionRepository(dbConfig)
  def userRepository: UserRepositoryInterface                 = new UserRepository(dbConfig, passwordHasherRegistry)

  implicit val bucketConfiguration: BucketConfiguration = BucketConfiguration(
    keyId = configuration.get[String]("alpakka.s3.aws.credentials.access-key-id"),
    secretKey = configuration.get[String]("alpakka.s3.aws.credentials.secret-access-key"),
    amazonBucketName = applicationConfiguration.amazonBucketName
  )

  def s3Service: S3ServiceInterface = new S3Service()

  //  Actor

  implicit val frontRoute: FrontRoute = new FrontRoute(signalConsoConfiguration)

  // Orchestrator

  val reportConsumerReviewOrchestrator =
    new ReportConsumerReviewOrchestrator(reportRepository, eventRepository, responseConsumerReviewRepository)

  val htmlFromTemplateGenerator = new HtmlFromTemplateGenerator(messagesApi, frontRoute)

  val reportOrchestrator = new ReportOrchestrator(
    reportConsumerReviewOrchestrator,
    reportRepository,
    eventRepository,
    userRepository,
    signalConsoConfiguration
  )

  logger.debug("Starting App and sending sentry alert")

  val reportListController =
    new ReportListController(
      reportOrchestrator,
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
