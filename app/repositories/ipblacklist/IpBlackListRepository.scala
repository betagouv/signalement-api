package repositories.ipblacklist

import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future

class IpBlackListRepository(dbConfig: DatabaseConfig[JdbcProfile]) extends IpBlackListRepositoryInterface {

  val table = IpBlackListTable.table

  import dbConfig._

  override def create(ip: BlackListedIp): Future[BlackListedIp] = db
    .run(
      table returning table += ip
    )

  override def delete(ip: String): Future[Int] = db.run(
    table.filter(_.ip === ip).delete
  )

  override def list(): Future[List[BlackListedIp]] = db.run(table.to[List].result)
}
