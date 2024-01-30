package repositories.website

import models.PaginatedResult
import models.company.Company

import models.investigation.InvestigationStatus

import models.website.Website
import models.website.WebsiteId
import models.website.IdentificationStatus
import repositories.TypedCRUDRepositoryInterface

import java.time.LocalDate
import java.time.OffsetDateTime
import scala.concurrent.Future

trait WebsiteRepositoryInterface extends TypedCRUDRepositoryInterface[Website, WebsiteId] {

  def validateAndCreate(newWebsite: Website): Future[Website]

  def searchValidWebsiteCountryAssociationByHost(host: String): Future[Seq[Website]]

  def removeOtherNonIdentifiedWebsitesWithSameHost(website: Website): Future[Int]

  def searchCompaniesByUrl(
      url: String
  ): Future[Seq[(Website, Company)]]

  def deprecatedSearchCompaniesByHost(
      url: String
  ): Future[Seq[(Website, Company)]]

  def listWebsitesCompaniesByReportCount(
      maybeHost: Option[String],
      identificationStatus: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int],
      investigationStatus: Option[Seq[InvestigationStatus]],
      start: Option[OffsetDateTime],
      end: Option[OffsetDateTime],
      hasAssociation: Option[Boolean],
      isOpen: Option[Boolean]
  ): Future[PaginatedResult[((Website, Option[Company]), Int)]]

  def searchValidAssociationByHost(host: String): Future[Seq[Website]]

  def getUnkonwnReportCountByHost(
      host: Option[String],
      start: Option[LocalDate] = None,
      end: Option[LocalDate] = None
  ): Future[List[(String, Int)]]

  def listNotAssociatedToCompany(host: String): Future[Seq[Website]]

  def listIdentified(host: String): Future[Seq[Website]]
}
