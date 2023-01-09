package utils

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api.Environment
import config.ApplicationConfiguration
import config.EmailConfiguration
import config.SignalConsoConfiguration
import config.TaskConfiguration
import loader.SignalConsoComponents
import org.flywaydb.core.Flyway
import org.specs2.mock.Mockito
import org.specs2.specification._
import play.api.Application
import play.api.ApplicationLoader
import play.api.Configuration
import play.api.db.slick.DefaultSlickApi
import play.api.db.slick.SlickApi
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.concurrent.ActorSystemProvider
import pureconfig.ConfigConvert
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import services.MailRetriesService
import services.MailRetriesService.EmailRequest
import tasks.company.CompanySyncServiceInterface
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext

trait AppSpec extends BeforeAfterAll with Mockito {

  val appEnv: play.api.Environment = play.api.Environment.simple(new File("."))
  val context: ApplicationLoader.Context = ApplicationLoader.Context.create(appEnv)

  implicit val localTimeInstance: ConfigConvert[LocalTime] = localTimeConfigConvert(DateTimeFormatter.ISO_TIME)
  implicit val personReader: ConfigReader[EmailAddress] = deriveReader[EmailAddress]
  val csvStringListReader = ConfigReader[String].map(_.split(",").toList)
  implicit val stringListReader = ConfigReader[List[String]].orElse(csvStringListReader)

  val applicationConfiguration: ApplicationConfiguration = ConfigSource.default.loadOrThrow[ApplicationConfiguration]

  val configLoader: SignalConsoConfiguration = applicationConfiguration.app
  val emailConfiguration: EmailConfiguration = applicationConfiguration.mail
  val taskConfiguration: TaskConfiguration = applicationConfiguration.task

  lazy val actorSystem: ActorSystem = new ActorSystemProvider(appEnv, context.initialConfiguration).get
  val executionContext: ExecutionContext = actorSystem.dispatcher
  val slickApi: SlickApi = new DefaultSlickApi(appEnv, context.initialConfiguration, new DefaultApplicationLifecycle())(
    executionContext
  )
//  val database: Database = SlickDBApi(slickApi).database("default")

  def setupData() = {}
  def cleanupData() = {}

  def beforeAll(): Unit = {
    databaseScript().clean()
    cleanupData()
    databaseScript().migrate()
    setupData()
  }
  def afterAll(): Unit = {}

  def databaseScript() = Flyway
    .configure()
    .dataSource(
      applicationConfiguration.flyway.jdbcUrl,
      applicationConfiguration.flyway.user,
      applicationConfiguration.flyway.password
    )
    .cleanDisabled(false)
    .load()

}

object TestApp {

  def buildApp(
      maybeAuthEnv: Option[Environment[AuthEnv]] = None,
      maybeApiKeyEnv: Option[Environment[APIKeyEnv]] = None,
      maybeConfiguration: Option[Configuration] = None
  ): (
      Application,
      SignalConsoComponents
  ) = {
    val appEnv: play.api.Environment = play.api.Environment.simple(new File("."))
    val context: ApplicationLoader.Context = ApplicationLoader.Context.create(appEnv)
    val loader = new DefaultApplicationLoader(maybeAuthEnv, maybeApiKeyEnv, maybeConfiguration)
    (loader.load(context), loader.components)
  }

  def buildApp(applicationLoader: ApplicationLoader): Application = {
    val appEnv: play.api.Environment = play.api.Environment.simple(new File("."))
    val context: ApplicationLoader.Context = ApplicationLoader.Context.create(appEnv)
    applicationLoader.load(context)
  }

}

class DefaultApplicationLoader(
    maybeAuthEnv: Option[Environment[AuthEnv]] = None,
    maybeApiKeyEnv: Option[Environment[APIKeyEnv]] = None,
    maybeConfiguration: Option[Configuration] = None
) extends ApplicationLoader
    with Mockito {
  var components: SignalConsoComponents = _

  val mailRetriesServiceMock = mock[MailRetriesService]

  doNothing.when(mailRetriesServiceMock).sendEmailWithRetries(any[EmailRequest])

  override def load(context: ApplicationLoader.Context): Application = {
    components = new SignalConsoComponents(context) {

      override def authEnv: Environment[AuthEnv] =
        maybeAuthEnv.getOrElse(super.authEnv)
      override lazy val mailRetriesService: MailRetriesService = mailRetriesServiceMock

      override def companySyncService: CompanySyncServiceInterface = new CompanySyncServiceMock()
      override def authApiEnv: Environment[APIKeyEnv] =
        maybeApiKeyEnv.getOrElse(super.authApiEnv)
      override def configuration: Configuration = maybeConfiguration.getOrElse(super.configuration)

    }
    components.application
  }

}
