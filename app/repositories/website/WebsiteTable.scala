package repositories.website

import models.website.Website
import models.website.WebsiteId
import models.website.IdentificationStatus
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID
import WebsiteColumnType._
import models.investigation.InvestigationStatus
import repositories.TypedDatabaseTable

class WebsiteTable(tag: Tag) extends TypedDatabaseTable[Website, WebsiteId](tag, "websites") {
  def creationDate = column[OffsetDateTime]("creation_date")
  def host = column[String]("host")
  def isMarketplace = column[Boolean]("is_marketplace")
  def companyCountry = column[Option[String]]("company_country")
  def companyId = column[Option[UUID]]("company_id")
  def identificationStatus = column[IdentificationStatus]("identification_status")
  def investigationStatus = column[InvestigationStatus]("investigation_status")
  def lastUpdated = column[OffsetDateTime]("last_updated")
  def * = (
    id,
    creationDate,
    host,
    isMarketplace,
    companyCountry,
    companyId,
    identificationStatus,
    investigationStatus,
    lastUpdated
  ) <> ((Website.apply _).tupled, Website.unapply)
}

object WebsiteTable {
  val table = TableQuery[WebsiteTable]
}
