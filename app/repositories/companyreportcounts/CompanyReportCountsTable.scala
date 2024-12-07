package repositories.companyreportcounts

import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import slick.lifted.Tag
import slick.lifted.TableQuery

import java.util.UUID
class CompanyReportCountsTable(tag: Tag) extends DatabaseTable[CompanyReportCounts](tag, "company_report_counts") {

  def companyId             = column[UUID]("company_id", O.PrimaryKey)
  def totalReports          = column[Long]("total_reports")
  def totalProcessedReports = column[Long]("total_processed_reports")

  def * = (companyId, totalReports, totalProcessedReports) <> (CompanyReportCounts.tupled, CompanyReportCounts.unapply)
}

object CompanyReportCountsTable {
  val table = TableQuery[CompanyReportCountsTable]
}
