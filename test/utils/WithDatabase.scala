package utils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification._

trait DatabaseSpec extends AroundEach {
  val databaseConfig = DatabaseConfig.forConfig[JdbcProfile]("slick.dbs.default")
  import databaseConfig.driver.api._

  private def runSqlSync[R](query: slick.dbio.DBIOAction[R, NoStream, Nothing]) = {
    Await.result(
      databaseConfig.db.run(query),
      1.second
    )
  }
  def around[R: AsResult](r: => R): Result = {
    runSqlSync(sql"BEGIN".as[String])
    try AsResult(r)
    finally runSqlSync(sql"ROLLBACK".as[String])
  }
}