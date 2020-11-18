package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.URL

import scala.concurrent.{ExecutionContext, Future}

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
    def companyId = column[UUID]("company_id")
    def kind = column[WebsiteKind]("kind")
    def * = (id, creationDate, host, companyId, kind) <> (Website.tupled, Website.unapply)
  }

  val websiteTableQuery = TableQuery[WebsiteTable]

  def create(website: Website): Future[Website] = db
    .run(websiteTableQuery += website)
    .map(_ => website)


  def addCompanyWebsite(host: String, companyId: UUID) =
    db.run(websiteTableQuery
      .filter(_.host === host)
      .filter(website => (website.kind === WebsiteKind.values.filter(_.isExlusive).bind.any) || (website.companyId === companyId))
      .result.headOption)
      .flatMap(_.map(Future(_))
        .getOrElse(db.run(websiteTableQuery returning websiteTableQuery += Website(
          UUID.randomUUID(),
          OffsetDateTime.now,
          host,
          companyId,
          kind = WebsiteKind.PENDING
        )))
      )

  def searchCompaniesByUrl(url: String) = {
    URL(url).getHost.map(host =>
      db.run(websiteTableQuery
        .filter(_.host === host)
        .filterNot(_.kind === WebsiteKind.PENDING)
        .join(companyRepository.companyTableQuery).on(_.companyId === _.id)
        .result
      )
    ).getOrElse(Future(Nil))
  }
}
