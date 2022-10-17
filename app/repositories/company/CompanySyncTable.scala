package repositories.company

import models.company.CompanySync
import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.time.OffsetDateTime
class CompanySyncTable(tag: Tag) extends DatabaseTable[CompanySync](tag, "company_sync") {

  def lastUpdated = column[OffsetDateTime]("last_updated")

  def * : ProvenShape[CompanySync] =
    (id, lastUpdated) <> ((CompanySync.apply _).tupled, CompanySync.unapply)

}
