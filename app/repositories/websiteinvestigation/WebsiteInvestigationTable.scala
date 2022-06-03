package repositories.websiteinvestigation

import models.investigation._
import models.website.WebsiteId
import repositories.PostgresProfile.api._
import repositories.TypedDatabaseTable
import repositories.website.WebsiteColumnType._
import repositories.websiteinvestigation.WebsiteInvestigationColumnType._

import java.time.OffsetDateTime

class WebsiteInvestigationTable(tag: Tag)
    extends TypedDatabaseTable[WebsiteInvestigation, WebsiteInvestigationId](tag, "website_investigation") {

  def websiteId = column[WebsiteId]("website_id")
  def practice = column[Option[Practice]]("practice")
  def investigationStatus = column[InvestigationStatus]("investigation_status")
  def attribution = column[Option[DepartmentDivision]]("attribution")
  def creationDate = column[OffsetDateTime]("creation_date")
  def lastUpdated = column[OffsetDateTime]("last_updated")

  def * = (
    id,
    websiteId,
    practice,
    investigationStatus,
    attribution,
    creationDate,
    lastUpdated
  ) <> ((WebsiteInvestigation.apply _).tupled, WebsiteInvestigation.unapply)
}

object WebsiteInvestigationTable {
  val table = TableQuery[WebsiteInvestigationTable]
}
