package repositories.website

import models._
import models.website.Website
import models.website.WebsiteId
import models.website.IdentificationStatus
import play.api.Logger
import repositories.PostgresProfile
import repositories.TypedCRUDRepository
import repositories.company.CompanyTable
import repositories.report.ReportTable
import repositories.website.WebsiteColumnType._
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import utils.URL

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import PostgresProfile.api._
import models.website.IdentificationStatus.NotIdentified
import slick.basic.DatabaseConfig

class WebsiteRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[WebsiteTable, Website, WebsiteId]
    with WebsiteRepositoryInterface {

  val logger: Logger = Logger(this.getClass())
  override val table: TableQuery[WebsiteTable] = WebsiteTable.table

  import dbConfig._

  override def validateAndCreate(newWebsite: Website): Future[Website] =
    db.run(
      table
        .filter(_.host === newWebsite.host)
        .filter(website =>
          (website.identificationStatus === IdentificationStatus.values.toList
            .filter(_ != NotIdentified)
            .bind
            .any) || (website.companyId === newWebsite.companyId)
        )
        .result
        .headOption
    ).flatMap(
      _.map(Future(_))
        .getOrElse(super.create(newWebsite))
    )

  override def searchValidWebsiteAssociationByHost(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(_.companyId.isEmpty)
        .filter(_.companyCountry.nonEmpty)
        .filter(_.identificationStatus inSet List(IdentificationStatus.Identified))
        .result
    )

  override def searchCompaniesByHost(
      host: String,
      identificationStatus: Option[Seq[IdentificationStatus]] = None,
      isMarketPlace: Option[Boolean]
  ): Future[Seq[(Website, Company)]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(w => identificationStatus.fold(true.bind)(w.identificationStatus.inSet(_)))
        .filterOpt(isMarketPlace) { case (websiteTable, isMarketplace) => websiteTable.isMarketplace === isMarketplace }
        .join(CompanyTable.table)
        .on(_.companyId === _.id)
        .result
    )

  override def removeOtherWebsitesWithSameHost(website: Website): Future[Int] =
    db.run(
      table
        .filter(_.host === website.host)
        .filterNot(_.id === website.id)
        .delete
    )

  override def searchCompaniesByUrl(
      url: String,
      identificationStatus: Option[Seq[IdentificationStatus]] = None,
      isMarketPlace: Option[Boolean]
  ): Future[Seq[(Website, Company)]] =
    URL(url).getHost.map(searchCompaniesByHost(_, identificationStatus, isMarketPlace)).getOrElse(Future(Nil))

  override def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      identificationStatus: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[((Website, Option[Company]), Int)]] = {
    val baseQuery =
      WebsiteTable.table
        .filterOpt(maybeHost) { case (websiteTable, filterHost) => websiteTable.host like s"%${filterHost}%" }
        .filter(websiteTable =>
          identificationStatus.fold(true.bind)(filteredKind => websiteTable.identificationStatus inSet filteredKind)
        )
        .filter { websiteTable =>
          websiteTable.companyId.nonEmpty || websiteTable.companyCountry.nonEmpty
        }
        .joinLeft(CompanyTable.table)
        .on(_.companyId === _.id)
        .joinLeft(ReportTable.table)
        .on { (tupleTable, reportTable) =>
          val (websiteTable, _) = tupleTable
          websiteTable.host === reportTable.host && reportTable.host.isDefined &&
          (websiteTable.companyId === reportTable.companyId || websiteTable.companyCountry === reportTable.companyCountry
            .map(_.asColumnOf[String]))
        }

    val query = baseQuery
      .groupBy(_._1)
      .map { case (grouped, all) => (grouped, all.map(_._2).size) }
      .sortBy { tupleTable =>
        val ((websiteTable, _), reportCount) = tupleTable
        (reportCount.desc, websiteTable.host.desc, websiteTable.id.desc)
      }
      .to[Seq]

    query.withPagination(db)(maybeOffset, maybeLimit)
  }

}
