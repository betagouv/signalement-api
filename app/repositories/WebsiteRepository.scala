package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import models._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcProfile
import utils.URL

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class WebsiteRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    val companyRepository: CompanyRepository,
    val reportRepository: ReportRepository
)(implicit ec: ExecutionContext) {

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
    .run(websiteTableQuery.filter(_.id === id).result.headOption)

  def update(website: Website): Future[Website] = {
    val query =
      for (refWebsite <- websiteTableQuery if refWebsite.id === website.id)
        yield refWebsite
    db.run(query.update(website))
      .map(_ => website)
  }

  def create(newWebsite: Website) =
    db.run(
      websiteTableQuery
        .filter(_.host === newWebsite.host)
        .filter(website =>
          (website.kind === WebsiteKind.values
            .filter(_.isExclusive)
            .bind
            .any) || (website.companyId === newWebsite.companyId)
        )
        .result
        .headOption
    ).flatMap(
      _.map(Future(_))
        .getOrElse(db.run(websiteTableQuery returning websiteTableQuery += newWebsite))
    )

  def searchCompaniesByHost(host: String, kinds: Option[Seq[WebsiteKind]] = None) =
    db.run(
      websiteTableQuery
        .filter(_.host === host)
        .filter(w => kinds.fold(true.bind)(w.kind.inSet(_)))
        .join(companyRepository.companyTableQuery)
        .on(_.companyId === _.id)
        .result
    )

  def searchCompaniesByUrl(url: String, kinds: Option[Seq[WebsiteKind]] = None): Future[Seq[(Website, Company)]] =
    URL(url).getHost.map(searchCompaniesByHost(_, kinds)).getOrElse(Future(Nil))

  def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[((Website, Company), Int)]] = {

    val baseQuery = websiteTableQuery
      .join(companyRepository.companyTableQuery)
      .on(_.companyId === _.id)
      .joinLeft(reportRepository.reportTableQuery)
      .on((c, r) => c._1.host === r.host && c._1.companyId === r.companyId)
      .filter(
        _._2.map(reportTable => reportTable.host.isDefined)
      )
      .filter(t => maybeHost.fold(true.bind)(h => t._2.fold(true.bind)(_.host.fold(true.bind)(_ like s"%${h}%"))))
      .filter(websiteCompanyTable =>
        kinds.fold(true.bind)(filteredKind => websiteCompanyTable._1._1.kind inSet filteredKind)
      )

    val query = baseQuery
      .groupBy(_._1)
      .map { case (grouped, all) => (grouped, all.map(_._2).size) }
      .sortBy(_._2.desc)
      .to[Seq]

    query.withPagination(db)(maybeOffset, maybeLimit)
  }

  def delete(id: UUID): Future[Int] = db.run(websiteTableQuery.filter(_.id === id).delete)
}
