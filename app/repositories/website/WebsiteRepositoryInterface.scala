package repositories.website

import models.Company
import models.PaginatedResult
import models.website.Website
import models.website.WebsiteKind
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

trait WebsiteRepositoryInterface extends CRUDRepositoryInterface[Website] {

  def validateAndCreate(newWebsite: Website): Future[Website]

  def searchValidWebsiteAssociationByHost(host: String): Future[Seq[Website]]

  def searchCompaniesByHost(host: String, kinds: Option[Seq[WebsiteKind]] = None): Future[Seq[(Website, Company)]]

  def removeOtherWebsitesWithSameHost(website: Website): Future[Int]

  def searchCompaniesByUrl(url: String, kinds: Option[Seq[WebsiteKind]] = None): Future[Seq[(Website, Company)]]

  def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[((Website, Option[Company]), Int)]]
}
