package tasks.company

import config.TaskConfiguration
import models.company.Company
import orchestrators.AlbertOrchestrator
import org.apache.pekko.actor.ActorSystem
import repositories.company.CompanyRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
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
    albertOrchestrator: AlbertOrchestrator,
    taskRepository: TaskRepositoryInterface
)(implicit
    executionContext: ExecutionContext
) extends ScheduledTask(13, "company_albert_label_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = FrequentTaskSettings(interval = 1.hour)

  private val COMPANIES_PROCESSED_EACH_RUN = 1000

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
      maybeLabel <- albertOrchestrator.genActivityLabelForCompany(company)
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

}
