package utils

import scala.concurrent.Await
import scala.concurrent.duration._

import play.Application
import play.api.Mode
import play.api.db.slick.DatabaseConfigProvider
import play.inject.guice.GuiceApplicationBuilder

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.specs2.Spec
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification._

trait AppSpec extends AroundEach with BeforeAll {
  lazy val app = new GuiceApplicationBuilder().build()
  def injector = app.asScala.injector
  lazy val dbConfig = injector.instanceOf[DatabaseConfigProvider].get[JdbcProfile]

  def setupData() {}

  def beforeAll(): Unit = {
    setupData
  }
  def around[R: AsResult](r: => R): Result = {
    import dbConfig.driver.api._
    val timeout = 1.second
    Await.result(dbConfig.db.run(sql"BEGIN".as[String]), timeout)
    try AsResult(r)
    finally Await.result(dbConfig.db.run(sql"ROLLBACK".as[String]), timeout)
  }
}
