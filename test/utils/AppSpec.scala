package utils

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.specification._
import play.api.db.DBApi
import play.api.db.evolutions._
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import services.MailerService
import slick.jdbc.JdbcProfile

trait AppSpec extends BeforeAfterAll with Mockito {

  class FakeModule extends AbstractModule with ScalaModule {
    override def configure() = {
      bind[MailerService].toInstance(mock[MailerService])
    }
  }

  lazy val app = new GuiceApplicationBuilder()
    .overrides(new FakeModule())
    .build()

  def injector = app.injector
  private lazy val database = injector.instanceOf[DBApi].database("default")

  def setupData() {}

  def beforeAll(): Unit = {
    Evolutions.applyEvolutions(database)
    setupData()
  }
  def afterAll(): Unit = {
    Evolutions.cleanupEvolutions(database)
    injector.instanceOf[DatabaseConfigProvider].get[JdbcProfile].db.close()
  }
}
