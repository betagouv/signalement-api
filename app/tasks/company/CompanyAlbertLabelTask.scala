package tasks.company

import config.TaskConfiguration
import models.company.Company
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.AlbertService
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings

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
    executionContext: ExecutionContext,
    materializer: Materializer
) extends ScheduledTask(5, "company_albert_label_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = FrequentTaskSettings(interval = 1.hour)

  val COMPANIES_PROCESSED_EACH_RUN = 1000
  val MAX_REPORTS_USED_BY_COMPANY  = 5

  override def runTask(): Future[Unit] = {
    val outdatedCutoffDate = OffsetDateTime.now().minusMonths(1)
    for {
      companies <- companyRepository.findWithOutdatedAlbertActivityLabel(
        outdatedCutoffDate,
        limit = COMPANIES_PROCESSED_EACH_RUN
      )
    } yield companies.foldLeft(Future.successful(())) { (previous, company) =>
      for {
        _ <- previous
        _ <- processCompany(company)
      } yield ()
    }
  }

  def processCompany(company: Company): Future[Unit] =
    for {
      reports <- reportRepository.getLatestMeaningfulReportsOfCompany(
        company.id,
        // we need a little big of margin: not all reports will have a description
        limit = MAX_REPORTS_USED_BY_COMPANY * 2
      )
      descriptions = reports.flatMap(_.getDescription).take(MAX_REPORTS_USED_BY_COMPANY)
      maybeLabel <- descriptions match {
        case Nil => Future.successful(None)
        case _ =>
          albertService
            .labelCompanyActivity(company.id, descriptions)
            .map(Some(_))
            .recover(_ => None)
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
