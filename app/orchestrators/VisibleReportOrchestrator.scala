package orchestrators

import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import controllers.error.AppError.ReportNotFound
import models.UserRole.Professionnel
import models._
import models.company.Address
import models.report.reportmetadata.ReportExtra
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class VisibleReportOrchestrator(
    reportRepository: ReportRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  def getVisibleReportForUser(reportId: UUID, user: User): Future[Option[ReportExtra]] =
    for {
      reportWithMetadata <- reportRepository.getFor(Some(user), reportId)
      report = reportWithMetadata.map(_.report)
      company <- report.flatMap(_.companyId).map(r => companyRepository.get(r)).flatSequence
      address = Address.merge(company.map(_.address), report.map(_.companyAddress))
      reportExtra = reportWithMetadata.map(r =>
        ReportExtra
          .from(r, company)
          .setAddress(address)
      )
      visibleReportExtra <-
        user.userRole match {
          case UserRole.DGCCRF | UserRole.DGAL | UserRole.SuperAdmin | UserRole.Admin | UserRole.ReadOnlyAdmin =>
            Future.successful(reportExtra)
          case Professionnel =>
            companiesVisibilityOrchestrator
              .fetchVisibleCompanies(user)
              .map(_.map(v => Some(v.company.siret)))
              .map { visibleSirets =>
                reportExtra
                  .filter(r => visibleSirets.contains(r.report.companySiret))
                  .map(_.copy(companyAlbertActivityLabel = None))
              }
        }
    } yield visibleReportExtra

  def getVisibleReportOrThrow(reportId: UUID, user: User): Future[ReportExtra] =
    for {
      maybeReport <- getVisibleReportForUser(reportId, user)
      report      <- maybeReport.liftTo[Future](ReportNotFound(reportId))
    } yield report

  def checkReportIsVisible(reportId: UUID, user: User): Future[Unit] =
    for {
      _ <- getVisibleReportOrThrow(reportId, user)
    } yield ()

}
