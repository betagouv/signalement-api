package tasks.company

import config.TaskConfiguration
import models.company.Company
import models.report.Report
import org.apache.pekko.actor.ActorSystem
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.AlbertService
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings
import utils.Logs.RichLogger

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
class CompanyAlbertLabelTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    companyRepository: CompanyRepositoryInterface,
    reportRepository: ReportRepositoryInterface,
    taskRepository: TaskRepositoryInterface,
    albertService: AlbertService
)(implicit
    executionContext: ExecutionContext
) extends ScheduledTask(5, "company_albert_label_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = FrequentTaskSettings(interval = 1.hour)

  private val COMPANIES_PROCESSED_EACH_RUN = 1000
  private val MAX_REPORTS_USED_BY_COMPANY  = 5

  override def runTask(): Future[Unit] = {
    val outdatedCutoffDate = OffsetDateTime.now().minusMonths(2)
    for {
      companies <- companyRepository.findWithOutdatedAlbertActivityLabel(
        outdatedCutoffDate,
        limit = COMPANIES_PROCESSED_EACH_RUN
      )
      _ <- companies.foldLeft(Future.successful(())) { (previous, company) =>
        for {
          _ <- previous
          _ <- processCompany(company)
        } yield ()
      }
    } yield ()
  }

  private def processCompany(company: Company): Future[Unit] = {
    logger.infoWithTitle(s"albert_label_company", s"Will try to label company ${company.siret}")
    for {
      reports <- reportRepository.getLatestMeaningfulReportsOfCompany(
        company.id,
        // we need a little big of margin:
        // - not all reports will have a description
        // - we deduplicate later by email
        limit = MAX_REPORTS_USED_BY_COMPANY * 3
      )
      descriptions =
        // One user could submit several reports in a row on the company,
        // thus distorting our data a bit
        deduplicateByEmail(reports)
          .flatMap(_.getDescription)
          .take(MAX_REPORTS_USED_BY_COMPANY)
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
      _ <- companyRepository.update(
        company.id,
        company.copy(
          albertActivityLabel = maybeLabel,
          // Process may fail for various reasons (ex: bad result from Albert, or no reports with description)
          // We still always update the date : we won't try to process this one again until it's expired
          albertUpdateDate = Some(OffsetDateTime.now())
        )
      )
    } yield ()
  }

  private def deduplicateByEmail(reports: Seq[Report]) =
    reports
      .groupBy(_.email)
      .map(_._2.head)
      .toSeq
}
