package repositories.website

import models.website.Website
import models.website.WebsiteId
import models.website.WebsiteKind
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID
import WebsiteColumnType._
import models.investigation.DepartmentDivision
import models.investigation.InvestigationStatus
import models.investigation.Practice
import repositories.TypedDatabaseTable

class WebsiteTable(tag: Tag) extends TypedDatabaseTable[Website, WebsiteId](tag, "websites") {
  def creationDate = column[OffsetDateTime]("creation_date")
  def host = column[String]("host")
  def companyCountry = column[Option[String]]("company_country")
  def companyId = column[Option[UUID]]("company_id")
  def kind = column[WebsiteKind]("kind")
  def practice = column[Option[Practice]]("practice")
  def investigationStatus = column[InvestigationStatus]("investigation_status")
  def attribution = column[Option[DepartmentDivision]]("attribution")
  def lastUpdated = column[OffsetDateTime]("last_updated")
  def * = (
    id,
    creationDate,
    host,
    companyCountry,
    companyId,
    kind,
    practice,
    investigationStatus,
    attribution,
    lastUpdated
  ) <> ((Website.apply _).tupled, Website.unapply)
}

object WebsiteTable {
  val table = TableQuery[WebsiteTable]
}
