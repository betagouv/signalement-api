package orchestrators

import models.CompanyReportsCountByDay
import models.CompanyReportsCountByMonth
import models.CompanyReportsCountByWeek
import models.CompanyReportsCountPeriod
import play.api.Logger
import repositories._

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyStatsOrchestrator @Inject() (
    _report: ReportRepository
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  def getReportsCountByDate(id: UUID, request: CompanyReportsCountPeriod): Future[Seq[(LocalDate, Int)]] =
    request match {
      case CompanyReportsCountByDay   => _report.getReportsCountByDay(id)
      case CompanyReportsCountByWeek  => _report.getReportsCountByWeek(id)
      case CompanyReportsCountByMonth => _report.getReportsCountByMonth(id)
      case _                          => _report.getReportsCountByMonth(id)
    }

  def getHosts(id: UUID) = _report.getHosts(id)

  def getReportsTagsDistribution(id: UUID) = _report.getReportsTagsDistribution(id)

  def getReportsStatusDistribution(id: UUID) = _report.getReportsStatusDistribution(id)
}
