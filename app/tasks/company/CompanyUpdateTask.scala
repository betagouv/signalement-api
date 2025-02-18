package tasks.company

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import cats.implicits.toTraverseOps
import config.TaskConfiguration
import models.company.CompanySync
import repositories.company.CompanyRepositoryInterface
import repositories.company.CompanySyncRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.DailyTaskSettings

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import utils.Logs.RichLogger

class CompanyUpdateTask(
    actorSystem: ActorSystem,
    companyRepository: CompanyRepositoryInterface,
    companySyncService: CompanySyncServiceInterface,
    companySyncRepository: CompanySyncRepositoryInterface,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit
    executionContext: ExecutionContext,
    materializer: Materializer
) extends ScheduledTask(5, "company_update_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.companyUpdate.startTime)

  // Be careful on how much stress you can put to the database, database task are queued into 1000 slot queue.
  // If more tasks are pushed than what the database can handle, it could result to RejectionException thus rejecting any call to database
  override def runTask(): Future[Unit] = for {
    companySync <- getCompanySync()
    res <- companyRepository.streamCompanies
      .grouped(300)
      .throttle(1, 1.second)
      .mapAsync(1) { companies =>
        logger.infoWithTitle(
          "company_update_task_item",
          s"Syncing ${companies.size} companies"
        )
        companySyncService.syncCompanies(companies)
      }
      .mapAsync(1)(updateSignalConsoCompaniesBySiret)
      .map(_.flatMap(_.lastUpdated).maxOption.getOrElse(companySync.lastUpdated))
      .toMat(computeLastUpdated(companySync.lastUpdated))((_, rightJediValue) => rightJediValue)
      .run()
      .flatMap(newLastUpdated => refreshLastUpdate(companySync, newLastUpdated))
      .map(_ => logger.info("Company update done"))
      .recoverWith { case e =>
        logger.errorWithTitle("company_update_failed", "Failed company update execution", e)
        throw e

      }
  } yield res

  private def getCompanySync(): Future[CompanySync] = companySyncRepository
    .list()
    .map(_.maxByOption(_.lastUpdated).getOrElse(CompanySync.default))

  def computeLastUpdated(originalLastUpdate: OffsetDateTime) =
    Sink.fold[OffsetDateTime, OffsetDateTime](originalLastUpdate) { (previousLastUpdate, newLastUpdate) =>
      if (previousLastUpdate.isAfter(newLastUpdate)) previousLastUpdate else newLastUpdate
    }

  private def refreshLastUpdate(companySync: CompanySync, newLastUpdated: OffsetDateTime) = for {
    lastUpdated <- getCompanySync().map(_.lastUpdated)
    _ <-
      if (newLastUpdated.isAfter(lastUpdated)) {
        logger.debug(s"New lastupdated company $newLastUpdated")
        companySyncRepository.createOrUpdate(companySync.copy(lastUpdated = newLastUpdated))
      } else Future.unit
  } yield ()

  private def updateSignalConsoCompaniesBySiret(companies: Seq[CompanySearchResult]) = {
    logger.debug(s"Syncing ${companies.size} companies")
    companies.map { c =>
      companyRepository
        .updateBySiret(
          c.siret,
          c.isOpen,
          c.isHeadOffice,
          c.isPublic,
          c.address.number,
          c.address.street,
          c.address.addressSupplement,
          c.name.getOrElse(""),
          c.brand,
          c.address.country
        )
        .map(_ => c)
    }.sequence
  }
}
