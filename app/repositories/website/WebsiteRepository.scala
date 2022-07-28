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
import models.investigation.DepartmentDivision
import models.investigation.InvestigationStatus
import models.investigation.Practice
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

  override def searchValidAssociationByHost(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(x => x.companyId.nonEmpty || x.companyCountry.nonEmpty)
        .filter(_.identificationStatus inSet List(IdentificationStatus.Identified))
        .result
    )

  override def searchValidWebsiteCountryAssociationByHost(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(_.companyId.isEmpty)
        .filter(_.companyCountry.nonEmpty)
        .filter(_.identificationStatus inSet List(IdentificationStatus.Identified))
        .result
    )

  private def searchCompaniesByHost(host: String): Future[Seq[(Website, Company)]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(_.identificationStatus inSet List(IdentificationStatus.Identified))
        .join(CompanyTable.table)
        .on(_.companyId === _.id)
        .result
    )

  override def removeOtherNonIdentifiedWebsitesWithSameHost(website: Website): Future[Int] =
    db.run(
      table
        .filter(_.host === website.host)
        .filterNot(_.id === website.id)
        .filterNot(_.identificationStatus inSet List(IdentificationStatus.Identified))
        .delete
    )

  override def searchCompaniesByUrl(
      url: String
  ): Future[Seq[(Website, Company)]] =
    URL(url).getHost.map(searchCompaniesByHost(_)).getOrElse(Future(Nil))

  override def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      identificationStatusFilter: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int],
      investigationStatusFilter: Option[Seq[InvestigationStatus]],
      practiceFilter: Option[Seq[Practice]],
      attributionFilter: Option[Seq[DepartmentDivision]]
  ): Future[PaginatedResult[((Website, Option[Company]), Int)]] = {

    println(s"------------------ attributionFilter = ${attributionFilter} ------------------")

    val baseQuery =
      WebsiteTable.table
        .filterOpt(maybeHost) { case (websiteTable, filterHost) => websiteTable.host like s"%${filterHost}%" }
        .filterOpt(identificationStatusFilter) { case (websiteTable, statusList) =>
          websiteTable.identificationStatus inSet statusList
        }
        .filterOpt(investigationStatusFilter) { case (websiteTable, statusList) =>
          websiteTable.investigationStatus inSet statusList
        }
        .filterOpt(practiceFilter) { case (websiteTable, practice) =>
          websiteTable.practice inSet practice
        }
        .filterOpt(attributionFilter) { case (websiteTable, attribution) =>
          websiteTable.attribution inSet attribution
        }
        .filter(_.isMarketplace === false)
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
