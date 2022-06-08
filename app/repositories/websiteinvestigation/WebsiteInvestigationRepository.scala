package repositories.websiteinvestigation

import models.Company
import models.PaginatedResult
import models.investigation.WebsiteInvestigation
import models.investigation.WebsiteInvestigationId
import models.website.Website
import models.website.WebsiteKind
import play.api.Logger
import repositories.PostgresProfile.api._
import repositories.TypedCRUDRepository
import repositories.company.CompanyTable
import repositories.report.ReportTable
import repositories.website.WebsiteColumnType._
import repositories.website.WebsiteTable
import repositories.websiteinvestigation.WebsiteInvestigationColumnType._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsiteInvestigationRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[WebsiteInvestigationTable, WebsiteInvestigation, WebsiteInvestigationId]
    with WebsiteInvestigationRepositoryInterface {

  val logger: Logger = Logger(this.getClass())
  import dbConfig._

  override val table: TableQuery[WebsiteInvestigationTable] = WebsiteInvestigationTable.table

  override def listWebsiteInvestigation(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[(((Website, Option[WebsiteInvestigation]), Option[Company]), Int)]] = {
    val baseQuery =
      WebsiteTable.table
        .filterOpt(maybeHost) { case (websiteTable, filterHost) => websiteTable.host like s"%${filterHost}%" }
        .filter(websiteTable => kinds.fold(true.bind)(filteredKind => websiteTable.kind inSet filteredKind))
        .filter { websiteTable =>
          websiteTable.companyId.nonEmpty || websiteTable.companyCountry.nonEmpty
        }
        .joinLeft(table)
        .on(_.id === _.websiteId)
        .joinLeft(CompanyTable.table)
        .on(_._1.companyId === _.id)
        .joinLeft(ReportTable.table)
        .on { (tupleTable, reportTable) =>
          val ((websiteTable, _), _) = tupleTable
          websiteTable.host === reportTable.host && reportTable.host.isDefined &&
          (websiteTable.companyId === reportTable.companyId || websiteTable.companyCountry === reportTable.companyCountry
            .map(_.asColumnOf[String]))
        }

    val query = baseQuery
      .groupBy(_._1)
      .map { case (grouped, all) => (grouped, all.map(_._2).size) }
      .sortBy { tupleTable =>
        val (((websiteTable, _), _), reportCount) = tupleTable
        (reportCount.desc, websiteTable.host.desc, websiteTable.id.desc)
      }
      .to[Seq]

    query.withPagination(db)(maybeOffset, maybeLimit)
  }
}
