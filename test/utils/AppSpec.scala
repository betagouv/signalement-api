package utils

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.specification._
import play.api.db.DBApi
import play.api.db.evolutions._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.mailer.AttachmentFile
import services.MailerService

trait AppSpec extends BeforeAfterAll with Mockito {

  def configureFakeModule(): AbstractModule = {
    new AppFakeModule
  }

  class AppFakeModule extends AbstractModule with ScalaModule {
    val mailerServiceMock = mock[MailerService]
    mailerServiceMock.attachmentSeqForWorkflowStepN(any[Int]) returns Seq()
    override def configure() = {
      bind[MailerService].toInstance(mailerServiceMock)
    }
  }

  lazy val app = new GuiceApplicationBuilder()
    .overrides(configureFakeModule())
    .build()

  def injector = app.injector
  private lazy val database = injector.instanceOf[DBApi].database("default")
  private lazy val company_database = injector.instanceOf[DBApi].database("company_db")

  def setupData() {}

  def beforeAll(): Unit = {
    Evolutions.applyEvolutions(database)
    Evolutions.applyEvolutions(company_database)
    setupData()
  }
  def afterAll(): Unit = {
    Evolutions.cleanupEvolutions(database)
    Evolutions.cleanupEvolutions(company_database)
    app.stop
  }
}
