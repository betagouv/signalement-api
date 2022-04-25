package repositories.website

import models._
import models.website.Website
import models.website.WebsiteKind
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile
import repositories.company.CompanyTable
import repositories.report.ReportTable
import repositories.website.WebsiteColumnType.WebsiteKindColumnType
import slick.jdbc.JdbcProfile
import utils.URL

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class WebsiteRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  def find(id: UUID): Future[Option[Website]] = db
    .run(WebsiteTable.table.filter(_.id === id).result.headOption)

  def update(website: Website): Future[Website] = {
    val query =
      for (refWebsite <- WebsiteTable.table if refWebsite.id === website.id)
        yield refWebsite
    db.run(query.update(website))
      .map(_ => website)
  }

  def create(newWebsite: Website) =
    db.run(
      WebsiteTable.table
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
        .getOrElse(db.run(WebsiteTable.table returning WebsiteTable.table += newWebsite))
    )

  def searchValidWebsiteAssociationByHost(host: String) =
    db.run(
      WebsiteTable.table
        .filter(_.host === host)
        .filter(_.companyId.isEmpty)
        .filter(_.companyCountry.nonEmpty)
        .filter(_.kind === WebsiteKind.DEFAULT)
        .result
    )

  def searchCompaniesByHost(host: String, kinds: Option[Seq[WebsiteKind]] = None) =
    db.run(
      WebsiteTable.table
        .filter(_.host === host)
        .filter(w => kinds.fold(true.bind)(w.kind.inSet(_)))
        .join(CompanyTable.table)
        .on(_.companyId === _.id)
        .result
    )

  def removeOtherWebsitesWithSameHost(website: Website) =
    db.run(
      WebsiteTable.table
        .filter(_.host === website.host)
        .filterNot(_.id === website.id)
        .delete
    )

  def searchCompaniesByUrl(url: String, kinds: Option[Seq[WebsiteKind]] = None): Future[Seq[(Website, Company)]] =
    URL(url).getHost.map(searchCompaniesByHost(_, kinds)).getOrElse(Future(Nil))

  def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[((Website, Option[Company]), Int)]] = {
    val baseQuery = WebsiteTable.table
      .joinLeft(CompanyTable.table)
      .on(_.companyId === _.id)
      .joinLeft(ReportTable.table)
      .on((c, r) =>
        c._1.host === r.host &&
          (c._1.companyId === r.companyId || c._1.companyCountry === r.companyCountry.map(_.asColumnOf[String]))
      )
      .filter(
        _._2.map(reportTable => reportTable.host.isDefined)
      )
      .filter(x => x._1._1.companyId.nonEmpty || x._1._1.companyCountry.nonEmpty)
      .filter(t => maybeHost.fold(true.bind)(h => t._2.fold(true.bind)(_.host.fold(true.bind)(_ like s"%${h}%"))))
      .filter(websiteCompanyTable =>
        kinds.fold(true.bind)(filteredKind => websiteCompanyTable._1._1.kind inSet filteredKind)
      )

    val query = baseQuery
      .groupBy(_._1)
      .map { case (grouped, all) => (grouped, all.map(_._2).size) }
      .sortBy(w => (w._2.desc, w._1._1.host.desc, w._1._1.id.desc))
      .to[Seq]

    query.withPagination(db)(maybeOffset, maybeLimit)
  }

  def delete(id: UUID): Future[Int] = db.run(WebsiteTable.table.filter(_.id === id).delete)
}