package repositories.consumer

import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class ConsumerRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._

  val query = ConsumerTable.table

  def create(consumer: Consumer) = db.run(query += consumer)
  def find(consumerId: UUID): Future[Option[Consumer]] =
    db.run(query.filter(_.id === consumerId).result.headOption)

  def getAll(): Future[Seq[Consumer]] =
    db.run(query.filter(_.deleteDate.isEmpty).result)

}
