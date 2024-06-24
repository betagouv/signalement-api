package repositories.ipblacklist

import repositories.PostgresProfile.api._
import repositories.TypedCRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class IpBlackListRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[IpBlackListTable, BlackListedIp, String]
    with IpBlackListRepositoryInterface {

  override val table = IpBlackListTable.table

}
