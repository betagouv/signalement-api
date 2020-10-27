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

  def addCompanyWebsite(host: String, companyId: UUID) =
    db.run(websiteTableQuery.filter(_.host === host).filter(_.companyId === companyId).result.headOption).flatMap(
      _.map(Future(_)).getOrElse(db.run(websiteTableQuery returning websiteTableQuery += Website(
        UUID.randomUUID(),
        OffsetDateTime.now,
        host,
        Some(companyId),
        kind = WebsiteKind.DEFAULT
      )))
    )

  def searchCompaniesByHost(url: String) = {
    URL(url).getHost.map(host =>
      for {
        websites <- db.run(websiteTableQuery.join(companyRepository.companyTableQuery).on(_.companyId === _.id).filter(_._1.host === host).result)
      } yield {
        websites.map(_._2).distinct
      }
    ).getOrElse(Future(Nil))
  }
}
