package repositories.report

import models.CountByDate
import models.PaginatedResult
import models.User
import models.barcode.BarcodeProduct
import models.company.Company
import models.report._
import models.report.reportmetadata.ReportWithMetadataAndBookmark
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import repositories.CRUDRepositoryInterface
import repositories.subcategorylabel.SubcategoryLabel
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

  def streamAll: DatabasePublisher[(((Report, Option[Company]), Option[BarcodeProduct]), Option[SubcategoryLabel])]
  def cloudWord(companyId: UUID): Future[List[ReportWordOccurrence]]

  def findSimilarReportList(
      report: ReportDraft,
      after: OffsetDateTime,
      extendedEmailComparison: Boolean
  ): Future[List[Report]]

  def countByDepartments(start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Future[Seq[(String, Int)]]

  def count(user: Option[User], filter: ReportFilter): Future[Int]

  def getMonthlyCount(user: Option[User], filter: ReportFilter, ticks: Int = 7): Future[Seq[CountByDate]]

  def getWeeklyCount(user: Option[User], filter: ReportFilter, ticks: Int): Future[Seq[CountByDate]]

  def getDailyCount(
      user: Option[User],
      filter: ReportFilter,
      ticks: Int
  ): Future[Seq[CountByDate]]

  def getReports(companyId: UUID): Future[List[Report]]

  // dead code
  def getWithWebsites(): Future[List[Report]]

  def getForWebsiteWithoutCompany(websiteHost: String): Future[List[Report]]

  // dead code
  def getWithPhones(): Future[List[Report]]

  def getReportsStatusDistribution(companyId: Option[UUID], user: User): Future[Map[String, Int]]
  def getAcceptedResponsesDistribution(companyId: UUID, user: User): Future[Map[ExistingResponseDetails, Int]]
  def getReportsTagsDistribution(companyId: Option[UUID], user: User): Future[Map[ReportTag, Int]]

  def getHostsOfCompany(companyId: UUID): Future[Seq[(String, Int)]]

  def getPhonesOfCompany(companyId: UUID): Future[Seq[(String, Int)]]
  def getReportsWithFiles(user: Option[User], filter: ReportFilter): Future[SortedMap[Report, List[ReportFile]]]

  def getReports(
      user: Option[User],
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int],
      sortBy: Option[ReportSort],
      orderBy: Option[SortOrder]
  ): Future[PaginatedResult[ReportWithMetadataAndBookmark]]

  def getReportsByIds(ids: List[UUID]): Future[List[Report]]

  def getByStatus(status: List[ReportStatus]): Future[List[Report]]

  def getByStatusAndExpired(status: List[ReportStatus], now: OffsetDateTime): Future[List[Report]]

  def getOldReportsNotRgpdDeleted(createdBefore: OffsetDateTime): Future[List[Report]]
  def getPendingReports(companiesIds: List[UUID]): Future[List[Report]]

  def getPhoneReports(start: Option[LocalDate], end: Option[LocalDate]): Future[List[Report]]

  def getPhoneReports(
      q: Option[String],
      start: Option[LocalDate],
      end: Option[LocalDate],
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[((Option[String], Option[SIRET], Option[String]), Int)]]

  def reportsCountBySubcategories(
      user: User,
      filters: ReportsCountBySubcategoriesFilter,
      lang: Locale
  ): Future[Seq[(String, List[String], Int, Int)]]

  def getFor(user: Option[User], id: UUID): Future[Option[ReportWithMetadataAndBookmark]]

  def getLatestReportsOfCompany(companyId: UUID, limit: Int): Future[List[Report]]
}
