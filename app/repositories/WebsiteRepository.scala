package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import play.api.Logger
import slick.jdbc.JdbcProfile

import models._
import utils.URL

@Singleton
class WebsiteRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)
                                     (implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  class WebsiteTable(tag: Tag) extends Table[Website](tag, "websites") {
    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def url = column[URL]("url")
    def companyId = column[Option[UUID]]("company_id")
    def * = (id, creationDate, url, companyId) <> (Website.tupled, Website.unapply)
  }

  val websiteTableQuery = TableQuery[WebsiteTable]

  def getOrCreate(url: URL): Future[Website] =
    db.run(websiteTableQuery.filter(_.url === url).result.headOption).flatMap(
      _.map(Future(_)).getOrElse(db.run(websiteTableQuery returning websiteTableQuery += Website(
        UUID.randomUUID(),
        OffsetDateTime.now,
        url,
        companyId = None
      )))
    )
}
