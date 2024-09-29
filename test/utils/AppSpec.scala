package utils

import org.apache.pekko.actor.ActorSystem
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
import play.api.mvc.Cookie
import pureconfig.ConfigConvert
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import services.antivirus.AntivirusServiceInterface
import services.emails.MailRetriesService.EmailRequest
import services.emails.MailRetriesService
import tasks.company.CompanySyncServiceInterface

import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.annotation.nowarn
import scala.concurrent.ExecutionContext

trait AppSpec extends BeforeAfterAll with Mockito {

  val appEnv: play.api.Environment       = play.api.Environment.simple(new File("."))
  val context: ApplicationLoader.Context = ApplicationLoader.Context.create(appEnv)

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

  val configLoader: SignalConsoConfiguration = applicationConfiguration.app
  val emailConfiguration: EmailConfiguration = applicationConfiguration.mail
  val taskConfiguration: TaskConfiguration   = applicationConfiguration.task

  lazy val actorSystem: ActorSystem      = new ActorSystemProvider(appEnv, context.initialConfiguration).get
  val executionContext: ExecutionContext = actorSystem.dispatcher
  val slickApi: SlickApi = new DefaultSlickApi(appEnv, context.initialConfiguration, new DefaultApplicationLifecycle())(
    executionContext
  )
//  val database: Database = SlickDBApi(slickApi).database("default")

  def setupData()   = {}
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
      maybeConfiguration: Option[Configuration] = None
  ): (
      Application,
      SignalConsoComponents
  ) = {
    val appEnv: play.api.Environment       = play.api.Environment.simple(new File("."))
    val context: ApplicationLoader.Context = ApplicationLoader.Context.create(appEnv)
    val loader                             = new DefaultApplicationLoader(maybeConfiguration)

    (loader.load(context), loader.components)
  }

  def buildApp(applicationLoader: ApplicationLoader): Application = {
    val appEnv: play.api.Environment       = play.api.Environment.simple(new File("."))
    val context: ApplicationLoader.Context = ApplicationLoader.Context.create(appEnv)
    applicationLoader.load(context)
  }

}

class DefaultApplicationLoader(
    maybeConfiguration: Option[Configuration] = None
) extends ApplicationLoader
    with Mockito {
  var components: SignalConsoComponents = _

  val mailRetriesServiceMock = mock[MailRetriesService]

  doNothing.when(mailRetriesServiceMock).sendEmailWithRetries(any[EmailRequest])

  override def load(context: ApplicationLoader.Context): Application = {
    components = new SignalConsoComponents(context) {

      override val s3Service = {

        new S3ServiceMock()
      }

      override lazy val mailRetriesService: MailRetriesService = mailRetriesServiceMock

      override def companySyncService: CompanySyncServiceInterface = new CompanySyncServiceMock()

      override def antivirusService: AntivirusServiceInterface = new AntivirusServiceMock()

      override def configuration: Configuration = maybeConfiguration.getOrElse(super.configuration)

    }
    components.application
  }

}
