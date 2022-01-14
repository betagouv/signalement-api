package utils

import com.google.inject.AbstractModule
import config.AppConfigLoader
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.specification._
import play.api.db.DBApi
import play.api.db.evolutions._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.mailer.Attachment
import services.MailerService

trait AppSpec extends BeforeAfterAll with Mockito {

  def configureFakeModule(): AbstractModule =
    new AppFakeModule

  class AppFakeModule extends AbstractModule with ScalaModule {
    val appConfigLoader = mock[AppConfigLoader]
    val mailerServiceMock = mock[MailerService]
    mailerServiceMock.sendEmail(
      any[EmailAddress],
      anyListOf[EmailAddress],
      anyListOf[EmailAddress],
      anyString,
      anyString,
      anyListOf[Attachment]
    ) returns ""

    override def configure() =
      bind[MailerService].toInstance(mailerServiceMock)
  }

  lazy val app = new GuiceApplicationBuilder()
    .overrides(configureFakeModule())
    .build()

  def injector = app.injector
  lazy val configLoader = injector.instanceOf[AppConfigLoader]
  lazy val config = configLoader.get

  private lazy val database = injector.instanceOf[DBApi].database("default")
  private lazy val company_database = injector.instanceOf[DBApi].database("company_db")

  def setupData() = {}
  def cleanupData() = {}

  def beforeAll(): Unit = {
    Evolutions.cleanupEvolutions(database)
    Evolutions.cleanupEvolutions(company_database)
    cleanupData()
    Evolutions.applyEvolutions(database)
    Evolutions.applyEvolutions(company_database)
    setupData()
  }
  def afterAll(): Unit =
    app.stop()
}
