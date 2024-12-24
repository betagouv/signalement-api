package orchestrators

import models.albert.AlbertProblemsResult
import models.company.Company
import models.report.Report
import play.api.Logger
import repositories.report.ReportRepositoryInterface
import services.AlbertService

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AlbertOrchestrator(
    val reportRepository: ReportRepositoryInterface,
    val albertService: AlbertService
)(implicit ec: ExecutionContext) {
  final val logger = Logger(getClass)

  def genActivityLabelForCompany(company: Company): Future[Option[String]] = {
    val maxReportsUsed = 5
    for {
      descriptions <- getLatestMeaningfulReportsDescsForCompany(
        company.id,
        maxReportsUsed
      )
      maybeLabel <- descriptions match {
        case Nil =>
          logger.info(s"Couldn't find enough usable reports for Albert for ${company.siret}")
          Future.successful(None)
        case _ =>
          albertService
            .labelCompanyActivity(company.id, descriptions)
            .recover { err =>
              logger.error(s"Didn't get a result from Albert for ${company.siret}", err)
              None
            }
      }
    } yield maybeLabel
  }

  def genProblemsForCompany(companyId: UUID): Future[Option[AlbertProblemsResult]] = {
    val maxReportsUsed = 30
    for {
      descriptions <- getLatestMeaningfulReportsDescsForCompany(
        companyId,
        maxReportsUsed
      )
      maybeResult <- descriptions match {
        case Nil =>
          logger.info(s"Not enough usable reports for Albert for company ${companyId}")
          Future.successful(None)
        case _ =>
          albertService
            .findProblems(companyId, descriptions)
            .recover { err =>
              logger.error(s"Didn't get a result from Albert for company $companyId", err)
              None
            }

      }
    } yield maybeResult
  }

  private def getLatestMeaningfulReportsDescsForCompany(companyId: UUID, maxReports: Int): Future[Seq[String]] =
    for {
      allLatestReports <- reportRepository.getLatestReportsOfCompany(
        companyId,
        // we need a little big of margin:
        // - not all reports will have a description
        // - we deduplicate later by email
        limit = maxReports * 2
      )
      meaningfulDescriptions =
        // One user could submit several reports in a row on the company,
        // thus distorting our data a bit
        deduplicateByEmail(allLatestReports)
          .flatMap(_.getDescription)
          .take(maxReports)
    } yield meaningfulDescriptions

  private def deduplicateByEmail(reports: Seq[Report]) =
    reports
      .groupBy(_.email)
      .map(_._2.head)
      .toSeq
}
