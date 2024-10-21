package repositories.report

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import models.report._
import models.CountByDate
import models.PaginatedResult
import models.UserRole
import models.barcode.BarcodeProduct
import models.company.Company
import models.report.reportmetadata.ReportWithMetadata
import repositories.CRUDRepositoryInterface
import slick.basic.DatabasePublisher
import utils.SIRET

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.collection.SortedMap
import scala.concurrent.Future

trait ReportRepositoryInterface extends CRUDRepositoryInterface[Report] {

  def streamReports: Source[Report, NotUsed]

  def streamAll: DatabasePublisher[((Report, Option[Company]), Option[BarcodeProduct])]

  def cloudWord(companyId: UUID): Future[List[ReportWordOccurrence]]

  def findSimilarReportList(
      report: ReportDraft,
      after: OffsetDateTime,
      extendedEmailComparison: Boolean
  ): Future[List[Report]]

  def countByDepartments(start: Option[LocalDate], end: Option[LocalDate]): Future[Seq[(String, Int)]]

  def count(userRole: Option[UserRole], filter: ReportFilter): Future[Int]

  def getMonthlyCount(userRole: Option[UserRole], filter: ReportFilter, ticks: Int = 7): Future[Seq[CountByDate]]

  def getWeeklyCount(userRole: Option[UserRole], filter: ReportFilter, ticks: Int): Future[Seq[CountByDate]]

  def getDailyCount(
      userRole: Option[UserRole],
      filter: ReportFilter,
      ticks: Int
  ): Future[Seq[CountByDate]]

  def getReports(companyId: UUID): Future[List[Report]]

  // dead code
  def getWithWebsites(): Future[List[Report]]

  def getForWebsiteWithoutCompany(websiteHost: String): Future[List[Report]]

  // dead code
  def getWithPhones(): Future[List[Report]]

  def getReportsStatusDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[String, Int]]
  def getAcceptedResponsesDistribution(companyId: UUID, userRole: UserRole): Future[Map[ExistingResponseDetails, Int]]
  def getReportsTagsDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[ReportTag, Int]]

  def getHostsByCompany(companyId: UUID): Future[Seq[String]]

  def getReportsWithFiles(userRole: Option[UserRole], filter: ReportFilter): Future[SortedMap[Report, List[ReportFile]]]

  def getReports(
      userRole: Option[UserRole],
      filter: ReportFilter,
      offset: Option[Long] = None,
      limit: Option[Int] = None
  ): Future[PaginatedResult[ReportWithMetadata]]

  def getReportsByIds(ids: List[UUID]): Future[List[Report]]

  def getByStatus(status: List[ReportStatus]): Future[List[Report]]

  def getByStatusAndExpired(status: List[ReportStatus], now: OffsetDateTime): Future[List[Report]]

  def getPendingReports(companiesIds: List[UUID]): Future[List[Report]]

  def getPhoneReports(start: Option[LocalDate], end: Option[LocalDate]): Future[List[Report]]

  def getPhoneReports(q: Option[String], start: Option[LocalDate], end: Option[LocalDate], offset: Option[Long], limit: Option[Int]): Future[PaginatedResult[((Option[String], Option[SIRET], Option[String], String), Int)]]

  def reportsCountBySubcategories(
      userRole: UserRole,
      filters: ReportsCountBySubcategoriesFilter,
      lang: Locale
  ): Future[Seq[(String, List[String], Int, Int)]]

  def getFor(userRole: Option[UserRole], id: UUID): Future[Option[ReportWithMetadata]]

}
