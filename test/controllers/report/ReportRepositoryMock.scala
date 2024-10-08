package controllers.report

import models.CountByDate
import models.PaginatedResult
import models.UserRole
import models.barcode.BarcodeProduct
import models.company.Company
import models.report._
import models.report.reportmetadata.ReportWithMetadata
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import repositories.report.ReportRepositoryInterface
import slick.basic.DatabasePublisher
import utils.CRUDRepositoryMock

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.collection.SortedMap
import scala.collection.mutable
import scala.concurrent.Future

class ReportRepositoryMock(database: mutable.Map[UUID, Report] = mutable.Map.empty[UUID, Report])
    extends CRUDRepositoryMock[Report](database, _.id)
    with ReportRepositoryInterface {

  def findSimilarReportList(
      report: ReportDraft,
      after: OffsetDateTime,
      extendedEmailComparison: Boolean
  ): Future[List[Report]] =
    ???

  override def countByDepartments(start: Option[LocalDate], end: Option[LocalDate]): Future[Seq[(String, Int)]] = ???

  override def count(userRole: Option[UserRole], filter: ReportFilter): Future[Int] = ???

  override def getMonthlyCount(userRole: Option[UserRole], filter: ReportFilter, ticks: Int): Future[Seq[CountByDate]] =
    ???

  override def getWeeklyCount(userRole: Option[UserRole], filter: ReportFilter, ticks: Int): Future[Seq[CountByDate]] =
    ???

  override def getDailyCount(userRole: Option[UserRole], filter: ReportFilter, ticks: Int): Future[Seq[CountByDate]] =
    ???

  override def getReports(companyId: UUID): Future[List[Report]] = ???

  override def getWithWebsites(): Future[List[Report]] = ???

  override def getWithPhones(): Future[List[Report]] = ???

  override def getReportsStatusDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[String, Int]] = ???

  override def getAcceptedResponsesDistribution(
      companyId: UUID,
      userRole: UserRole
  ): Future[Map[ExistingResponseDetails, Int]] = ???

  override def getReportsTagsDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[ReportTag, Int]] =
    ???

  override def getHostsByCompany(companyId: UUID): Future[Seq[String]] = ???

  override def getReportsWithFiles(
      userRole: Option[UserRole],
      filter: ReportFilter
  ): Future[SortedMap[Report, List[ReportFile]]] = ???

  override def getReports(
      userRole: Option[UserRole],
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[ReportWithMetadata]] = ???

  override def getReportsByIds(ids: List[UUID]): Future[List[Report]] = ???

  override def getByStatus(status: List[ReportStatus]): Future[List[Report]] = ???

  override def getByStatusAndExpired(status: List[ReportStatus], now: OffsetDateTime): Future[List[Report]] = ???

  override def getPendingReports(companiesIds: List[UUID]): Future[List[Report]] = ???

  override def getPhoneReports(start: Option[LocalDate], end: Option[LocalDate]): Future[List[Report]] = ???

  override def cloudWord(companyId: UUID): Future[List[ReportWordOccurrence]] = ???

  override def reportsCountBySubcategories(
      userRole: UserRole,
      filters: ReportsCountBySubcategoriesFilter,
      lang: Locale
  ): Future[Seq[(String, List[String], Int, Int)]] = ???

  override def getForWebsiteWithoutCompany(websiteHost: String): Future[List[Report]] = ???

  override def getFor(userRole: Option[UserRole], id: UUID): Future[Option[ReportWithMetadata]] = {
    val maybeReport = userRole match {
      case Some(UserRole.SuperAdmin)    => database.get(id)
      case Some(UserRole.Admin)         => database.get(id)
      case Some(UserRole.ReadOnlyAdmin) => database.get(id)
      case Some(UserRole.DGCCRF)        => database.get(id)
      case Some(UserRole.DGAL)          => database.get(id)
      case Some(UserRole.Professionnel) => database.get(id).filter(_.visibleToPro)
      case None                         => database.get(id)
    }
    Future.successful(maybeReport.map(ReportWithMetadata(_, None)))
  }

  def streamAll: DatabasePublisher[((Report, Option[Company]), Option[BarcodeProduct])] = ???

  override def streamReports: Source[Report, NotUsed] = ???
}
