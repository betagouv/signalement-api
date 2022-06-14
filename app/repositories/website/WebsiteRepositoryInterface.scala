package repositories.website

import models.Company
import models.PaginatedResult
import models.website.Website
import models.website.WebsiteId
import models.website.IdentificationStatus
import repositories.TypedCRUDRepositoryInterface

import scala.concurrent.Future

trait WebsiteRepositoryInterface extends TypedCRUDRepositoryInterface[Website, WebsiteId] {

  def validateAndCreate(newWebsite: Website): Future[Website]

  def searchValidWebsiteAssociationByHost(host: String): Future[Seq[Website]]

  def searchCompaniesByHost(
      host: String,
      identificationStatus: Option[Seq[IdentificationStatus]] = None,
      isMarketPlace: Option[Boolean]
  ): Future[Seq[(Website, Company)]]

  def removeOtherWebsitesWithSameHost(website: Website): Future[Int]

  def searchCompaniesByUrl(
      url: String,
      identificationStatus: Option[Seq[IdentificationStatus]] = None,
      isMarketPlace: Option[Boolean] = None
  ): Future[Seq[(Website, Company)]]

  def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      identificationStatus: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[((Website, Option[Company]), Int)]]
}
