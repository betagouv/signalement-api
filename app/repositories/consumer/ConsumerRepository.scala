package repositories.consumer

import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ConsumerRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[ConsumerTable, Consumer]
    with ConsumerRepositoryInterface {

  override val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._

  override val table = ConsumerTable.table

  override def getAll(): Future[Seq[Consumer]] =
    db.run(table.filter(_.deleteDate.isEmpty).result)

}
