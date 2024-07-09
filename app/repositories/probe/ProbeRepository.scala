package repositories.probe

import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ProbeRepository(dbConfig: DatabaseConfig[JdbcProfile]) {

  import dbConfig._

  def getReponseConsoPercentage(interval: FiniteDuration): Future[Option[Double]] = db.run(
    sql"""
      SELECT (CAST(SUM(
        CASE
          WHEN forward_to_reponseconso = true THEN 1 ELSE 0
        END) AS FLOAT) / count(*)) * 100 ratio
      FROM reports
      WHERE creation_date > (now() - INTERVAL '#${interval.toString()}');"""
      .as[Double]
      .headOption
  )

}
