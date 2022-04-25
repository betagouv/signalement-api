package repositories.report

import com.google.inject.ImplementedBy
import models.report._
import models.CountByDate
import models.PaginatedResult
import models.UserRole
import utils.EmailAddress

import java.time.LocalDate
import java.util.UUID
import scala.collection.SortedMap
import scala.concurrent.Future

@ImplementedBy(classOf[ReportRepository])
trait ReportRepositoryInterface {

  def findSimilarReportCount(report: Report): Future[Int]

  def create(report: Report): Future[Report]

  def list: Future[List[Report]]

  def findByEmail(email: EmailAddress): Future[Seq[Report]]

  def countByDepartments(start: Option[LocalDate], end: Option[LocalDate]): Future[Seq[(String, Int)]]

  def update(report: Report): Future[Report]

  def count(filter: ReportFilter): Future[Int]

  def getMonthlyCount(filter: ReportFilter, ticks: Int = 7): Future[Seq[CountByDate]]

  def getDailyCount(
      filter: ReportFilter,
      ticks: Int
  ): Future[Seq[CountByDate]]

  def getReport(id: UUID): Future[Option[Report]]

  def delete(id: UUID): Future[Int]

  def getReports(companyId: UUID): Future[List[Report]]

  def getWithWebsites(): Future[List[Report]]

  def getWithPhones(): Future[List[Report]]

  def getReportsStatusDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[String, Int]]

  def getReportsTagsDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[ReportTag, Int]]

  def getHostsByCompany(companyId: UUID): Future[Seq[String]]

  def getReportsWithFiles(filter: ReportFilter): Future[SortedMap[Report, List[ReportFile]]]

  def getReports(
      filter: ReportFilter,
      offset: Option[Long] = None,
      limit: Option[Int] = None
  ): Future[PaginatedResult[Report]]

  def getReportsByIds(ids: List[UUID]): Future[List[Report]]

  def getByStatus(status: ReportStatus): Future[List[Report]]

  def getPendingReports(companiesIds: List[UUID]): Future[List[Report]]

  def getWebsiteReportsWithoutCompany(
      start: Option[LocalDate] = None,
      end: Option[LocalDate] = None
  ): Future[List[Report]]

  def getUnkonwnReportCountByHost(
      host: Option[String],
      start: Option[LocalDate] = None,
      end: Option[LocalDate] = None
  ): Future[List[(Option[String], Int)]]

  def getPhoneReports(start: Option[LocalDate], end: Option[LocalDate]): Future[List[Report]]

}