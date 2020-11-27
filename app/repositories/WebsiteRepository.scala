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
    def * = (id, creationDate, host, companyId, kind) <> ((Website.apply _).tupled, Website.unapply)
  }

  val websiteTableQuery = TableQuery[WebsiteTable]

  def find(id: UUID): Future[Option[Website]] = db
    .run(websiteTableQuery.filter(_.id === id).to[List].result.headOption)

  def update(website: Website): Future[Website] = {
    val query = for (refWebsite <- websiteTableQuery if refWebsite.id === website.id)
      yield refWebsite
    db.run(query.update(website))
      .map(_ => website)
  }

  def create(newWebsite: Website) =
    db.run(websiteTableQuery
      .filter(_.host === newWebsite.host)
      .filter(website => (website.kind === WebsiteKind.values.filter(_.isExlusive).bind.any) || (website.companyId === newWebsite.companyId))
      .result.headOption)
      .flatMap(_.map(Future(_))
      .getOrElse(db.run(websiteTableQuery returning websiteTableQuery += newWebsite)))

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

  def list() = db.run(websiteTableQuery.result)

  def delete(id: UUID): Future[Int] = db.run(websiteTableQuery.filter(_.id === id).delete)

}
