package repositories.company.accessinheritancemigration

import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape
import slick.lifted.Tag

import java.time.OffsetDateTime
import java.util.UUID

class CompanyAccessInheritanceMigrationTable(tag: Tag)
    extends DatabaseTable[CompanyAccessInheritanceMigration](tag, "companies_access_inheritance_migration") {

  def companyId   = column[UUID]("company_id")
  def processedAt = column[OffsetDateTime]("processed_at")

  type Data = (
      UUID,
      OffsetDateTime
  )

  def construct: Data => CompanyAccessInheritanceMigration = { case (a, b) =>
    CompanyAccessInheritanceMigration(a, b)
  }

  def extract: PartialFunction[CompanyAccessInheritanceMigration, Data] = {
    case CompanyAccessInheritanceMigration(a, b) =>
      (a, b)
  }

  override def * : ProvenShape[CompanyAccessInheritanceMigration] =
    (
      companyId,
      processedAt
    ) <> (construct, extract.lift)
}

object CompanyAccessInheritanceMigrationTable {
  val table = TableQuery[CompanyAccessInheritanceMigrationTable]
}
