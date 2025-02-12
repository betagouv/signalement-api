package repositories.website

import models.PaginatedResult
import models.company.Company
import models.investigation.InvestigationStatus
import models.website.Website
import models.website.WebsiteId
import models.website.IdentificationStatus
import repositories.TypedCRUDRepositoryInterface
import tasks.website.ExtractionResultApi

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

trait WebsiteRepositoryInterface extends TypedCRUDRepositoryInterface[Website, WebsiteId] {

  def validateAndCreate(newWebsite: Website): Future[Website]

  def searchValidWebsiteCountryAssociationByHost(host: String): Future[Seq[Website]]

  def removeOtherNonIdentifiedWebsitesWithSameHost(website: Website): Future[Int]

  def searchCompaniesByUrl(
      url: String,
      nb: Int
  ): Future[Seq[((Website, Int), Company)]]

  def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      identificationStatus: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int],
      investigationStatus: Option[Seq[InvestigationStatus]],
      start: Option[OffsetDateTime],
      end: Option[OffsetDateTime],
      hasAssociation: Option[Boolean],
      isOpen: Option[Boolean],
      isMarketplace: Option[Boolean]
  ): Future[PaginatedResult[(Website, Option[Company], Option[ExtractionResultApi], Int)]]

  def searchValidAssociationByHost(host: String): Future[Seq[Website]]

  def getUnkonwnReportCountByHost(
      host: Option[String],
      start: Option[LocalDate],
      end: Option[LocalDate]
  ): Future[List[(String, Int)]]

  def getUnkonwnReportCountByHost(
      host: Option[String],
      start: Option[LocalDate],
      end: Option[LocalDate],
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[(String, Int)]]

  def listNotAssociatedToCompany(host: String): Future[Seq[Website]]

  def listIdentified(host: String): Future[Seq[Website]]

  def searchByCompaniesId(ids: List[UUID]): Future[Seq[(Website)]]

  def listByHost(host: String): Future[Seq[Website]]
}
