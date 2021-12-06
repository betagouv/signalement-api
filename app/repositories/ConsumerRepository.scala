package repositories

import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.Future

class ConsumerTable(tag: Tag) extends Table[Consumer](tag, "consumer") {

  def id = column[UUID]("id", O.PrimaryKey)
  def name = column[String]("name")
  def creationDate = column[OffsetDateTime]("creation_date")
  def apiKey = column[String]("api_key")
  def deleteDate = column[Option[OffsetDateTime]]("delete_date")

  def * = (
    id,
    name,
    creationDate,
    apiKey,
    deleteDate
  ) <> (Consumer.tupled, Consumer.unapply)
}

object ConsumerTables {
  val tables = TableQuery[ConsumerTable]
}

@Singleton
class ConsumerRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._

  val query = ConsumerTables.tables

  def getAll(): Future[Seq[Consumer]] =
    db.run(query.filter(_.deleteDate.isEmpty).result)

}
