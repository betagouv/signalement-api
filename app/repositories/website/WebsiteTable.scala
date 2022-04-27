package repositories.website

import models.website.Website
import models.website.WebsiteKind
import repositories.PostgresProfile.api._
import java.time.OffsetDateTime
import java.util.UUID
import WebsiteColumnType.WebsiteKindColumnType

class WebsiteTable(tag: Tag) extends Table[Website](tag, "websites") {
  def id = column[UUID]("id", O.PrimaryKey)
  def creationDate = column[OffsetDateTime]("creation_date")
  def host = column[String]("host")
  def companyCountry = column[Option[String]]("company_country")
  def companyId = column[Option[UUID]]("company_id")
  def kind = column[WebsiteKind]("kind")
  def * = (id, creationDate, host, companyCountry, companyId, kind) <> ((Website.apply _).tupled, Website.unapply)
}

object WebsiteTable {
  val table = TableQuery[WebsiteTable]
}
