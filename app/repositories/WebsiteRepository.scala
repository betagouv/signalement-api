package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import play.api.Logger
import slick.jdbc.JdbcProfile

import models._
import util.Try
import utils.URL

@Singleton
class WebsiteRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, val companyRepository: CompanyRepository)
                                     (implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  implicit val WebsiteKindColumnType = MappedColumnType.base[WebsiteKind, String](_.value, WebsiteKind.fromValue(_))

  class WebsiteTable(tag: Tag) extends Table[Website](tag, "websites") {
    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def host = column[String]("host")
    def companyId = column[Option[UUID]]("company_id")
    def kind = column[WebsiteKind]("kind")
    def * = (id, creationDate, host, companyId, kind) <> (Website.tupled, Website.unapply)
  }

  val websiteTableQuery = TableQuery[WebsiteTable]

  def getOrCreate(host: String) =
    db.run(websiteTableQuery.filter(_.host === host).result.headOption).flatMap(
      _.map(Future(_)).getOrElse(db.run(websiteTableQuery returning websiteTableQuery += Website(
        UUID.randomUUID(),
        OffsetDateTime.now,
        host,
        companyId = None,
        kind = WebsiteKind.DEFAULT
      )))
    )

  def fetchCompany(url: String) = {
    URL(url).getHost.map(host =>
      for {
        website <- db.run(websiteTableQuery.filter(_.host === host).result.headOption)
        company <- website.flatMap(_.companyId).map(companyRepository.fetchCompany(_)).getOrElse(Future(None))
      } yield company
    ).getOrElse(Future(None))
  }
}
