package utils

import play.Application
import play.api.db.DBApi
import play.api.db.evolutions._
import play.inject.guice.GuiceApplicationBuilder

import org.specs2.specification._

trait AppSpec extends BeforeAfterAll {
  lazy val app = new GuiceApplicationBuilder().build()
  def injector = app.asScala.injector
  private lazy val database = injector.instanceOf[DBApi].database("default")

  def setupData() {}

  def beforeAll(): Unit = {
    Evolutions.applyEvolutions(database)
    setupData()
  }
  def afterAll(): Unit = {
    Evolutions.cleanupEvolutions(database)
  }
}
