package repositories.website

import models._
import models.company.Company
import models.investigation.InvestigationStatus
import models.website.IdentificationStatus.NotIdentified
import models.website.IdentificationStatus
import models.website.Website
import models.website.WebsiteId
import play.api.Logger
import repositories.PostgresProfile.api._
import repositories.PaginateOps
import repositories.TypedCRUDRepository
import repositories.company.CompanyTable
import repositories.report.ReportTable
import repositories.siretextraction.SiretExtractionTable
import repositories.website.WebsiteColumnType._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import tasks.website.ExtractionResultApi
import utils.URL

import java.time._
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsiteRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[WebsiteTable, Website, WebsiteId]
    with WebsiteRepositoryInterface {

  val logger: Logger                           = Logger(this.getClass)
  override val table: TableQuery[WebsiteTable] = WebsiteTable.table

  import dbConfig._

  override def validateAndCreate(newWebsite: Website): Future[Website] = db.run(
    (for {
      maybeWebsite <- table
        .filter(_.host === newWebsite.host)
        .filter { website =>
          val hasBeenAlreadyIdentifiedByConso =
            (website.companyId === newWebsite.companyId) || (website.companyCountry === newWebsite.companyCountry) || (website.companyCountry.isEmpty && website.companyId.isEmpty)

          val hasBeenIdentifiedByAdmin = website.identificationStatus ===
            IdentificationStatus.values.toList
              .filter(_ != NotIdentified)
              .bind
              .any
          hasBeenIdentifiedByAdmin || hasBeenAlreadyIdentifiedByConso
        }
        .result
        .headOption
      website <- maybeWebsite match {
        case Some(website) => DBIO.successful(website)
        case None          => table returning table += newWebsite
      }
    } yield website).transactionally
  )

  override def searchValidAssociationByHost(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(x => x.companyId.nonEmpty || x.companyCountry.nonEmpty)
        .filter(_.identificationStatus === (IdentificationStatus.Identified: IdentificationStatus))
        .result
    )

  override def searchValidWebsiteCountryAssociationByHost(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(_.companyId.isEmpty)
        .filter(_.companyCountry.nonEmpty)
        .filter(_.identificationStatus === (IdentificationStatus.Identified: IdentificationStatus))
        .result
    )

  private def searchCompaniesByHost(host: String, nb: Int): Future[Seq[((Website, Int), Company)]] =
    db.run(
      table
        .filter(_.identificationStatus === (IdentificationStatus.Identified: IdentificationStatus))
        // Étape 1 : Sélectionner les résultats proches avec pg_trgm
        .filter(result => result.host % host)
        .sortBy(result => (result.host <-> host).asc)
        .take(10) // Réduire le champ des candidats
        // Étape 2 : Affiner avec levenshtein
        .filter(result => LevenshteinFunction(result.host, host) <= 4)
        .map(result => (result, LevenshteinFunction(result.host, host)))
        .sortBy(_._2.asc)
        .take(nb)
        .join(CompanyTable.table)
        .on { case ((websiteTable, _), companyTable) =>
          websiteTable.companyId === companyTable.id && companyTable.isOpen
        }
        .result
    )

  def searchByCompaniesId(ids: List[UUID]): Future[Seq[(Website)]] =
    db.run(
      table.filter { result =>
        result.companyId inSet ids
      }.result
    )

  def deprecatedSearchCompaniesByHost(host: String): Future[Seq[(Website, Company)]] =
    URL(host).getHost match {
      case Some(host) =>
        db.run(
          table
            .filter(_.host === host)
            .filter(_.identificationStatus === (IdentificationStatus.Identified: IdentificationStatus))
            .join(CompanyTable.table)
            .on(_.companyId === _.id)
            .result
        )
      case None => Future.successful(Nil)
    }

  override def removeOtherNonIdentifiedWebsitesWithSameHost(website: Website): Future[Int] =
    db.run(
      table
        .filter(_.host === website.host)
        .filterNot(_.id === website.id)
        .filterNot(_.identificationStatus === (IdentificationStatus.Identified: IdentificationStatus))
        .delete
    )

  override def searchCompaniesByUrl(
      url: String,
      nb: Int
  ): Future[Seq[((Website, Int), Company)]] =
    URL(url).getHost match {
      case Some(host) => searchCompaniesByHost(host, nb)
      case None       => Future.successful(Nil)
    }

  override def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      identificationStatusFilter: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int],
      investigationStatusFilter: Option[Seq[InvestigationStatus]],
      start: Option[OffsetDateTime],
      end: Option[OffsetDateTime],
      hasAssociation: Option[Boolean],
      isOpen: Option[Boolean],
      isMarketplace: Option[Boolean]
  ): Future[PaginatedResult[(Website, Option[Company], Option[ExtractionResultApi], Int)]] = {

    val baseQuery =
      WebsiteTable.table
        .filterOpt(isMarketplace) { case (websiteTable, isMarketplaceFilter) =>
          websiteTable.isMarketplace === isMarketplaceFilter
        }
        .filterOpt(maybeHost) { case (websiteTable, filterHost) => websiteTable.host like s"%${filterHost}%" }
        .filterOpt(identificationStatusFilter) { case (websiteTable, statusList) =>
          websiteTable.identificationStatus inSet statusList
        }
        .filterOpt(investigationStatusFilter) { case (websiteTable, statusList) =>
          websiteTable.investigationStatus inSet statusList
        }
        .filterOpt(hasAssociation) {
          case (table, true) =>
            table.companyCountry.isDefined || table.companyId.isDefined
          case (table, false) =>
            table.companyCountry.isEmpty || table.companyId.isEmpty
        }
        .joinLeft(CompanyTable.table)
        .on(_.companyId === _.id)
        .joinLeft(SiretExtractionTable.table)
        .on(_._1.host === _.host)
        .joinLeft(ReportTable.table)
        .on { (tupleTable, reportTable) =>
          val ((websiteTable, _), _) = tupleTable
          websiteTable.host === reportTable.host && reportTable.host.isDefined
        }
        .filterOpt(isOpen) { case ((((_, companyTable), _), _), isOpenFilter) =>
          companyTable.map(_.isOpen === isOpenFilter)
        }
        .filterOpt(start) { case ((((websiteTable, _), _), reportTable), start) =>
          reportTable.map(_.creationDate >= start).getOrElse(websiteTable.creationDate >= start)
        }
        .filterOpt(end) { case ((((websiteTable, _), _), reportTable), end) =>
          reportTable.map(_.creationDate <= end).getOrElse(websiteTable.creationDate <= end)
        }

    val query = baseQuery
      .groupBy(_._1)
      .map { case (grouped, all) => (grouped._1._1, grouped._1._2, grouped._2, all.map(_._2).size) }
      .sortBy { tupleTable =>
        val (websiteTable, _, _, reportCount) = tupleTable
        (reportCount.desc, websiteTable.host.desc, websiteTable.id.desc)
      }
      .to[Seq]

    query.withPagination(db)(maybeOffset, maybeLimit)
  }

  def getUnkonwnReportCountByHost(
      host: Option[String],
      start: Option[LocalDate],
      end: Option[LocalDate]
  ): Future[List[(String, Int)]] = db
    .run(
      WebsiteTable.table
        .filter(t => host.fold(true.bind)(h => t.host like s"%${h}%"))
        .filter(x => x.companyId.isEmpty && x.companyCountry.isEmpty)
        .filterOpt(start) { case (table, start) =>
          table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .joinLeft(ReportTable.table)
        .on { (websiteTable, reportTable) =>
          websiteTable.host === reportTable.host && reportTable.host.isDefined
        }
        .groupBy(_._1.host)
        .map { case (host, report) => (host, report.map(_._2).size) }
        .sortBy(_._2.desc)
        .to[List]
        .result
    )

  def getUnkonwnReportCountByHost(
      host: Option[String],
      start: Option[LocalDate],
      end: Option[LocalDate],
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[(String, Int)]] =
    WebsiteTable.table
      .filter(t => host.fold(true.bind)(h => t.host like s"%${h}%"))
      .filter(x => x.companyId.isEmpty && x.companyCountry.isEmpty)
      .filterOpt(start) { case (table, start) =>
        table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
      }
      .filterOpt(end) { case (table, end) =>
        table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
      }
      .joinLeft(ReportTable.table)
      .on { (websiteTable, reportTable) =>
        websiteTable.host === reportTable.host && reportTable.host.isDefined
      }
      .groupBy(_._1.host)
      .map { case (host, report) => (host, report.map(_._2).size) }
      .sortBy(_._2.desc)
      .withPagination(db)(
        maybeOffset = offset,
        maybeLimit = limit,
        maybePreliminaryAction = None
      )

  def listNotAssociatedToCompany(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(website =>
          website.companyCountry.isDefined || (website.identificationStatus inSet List(
            IdentificationStatus.NotIdentified
          ))
        )
        .result
    )

  def listIdentified(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .filter(_.identificationStatus inSet List(IdentificationStatus.Identified))
        .result
    )

  def listByHost(host: String): Future[Seq[Website]] =
    db.run(
      table
        .filter(_.host === host)
        .result
    )
}
