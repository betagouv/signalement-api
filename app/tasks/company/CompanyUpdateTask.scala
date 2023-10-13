package tasks.company

import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.stream.Materializer
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.alpakka.slick.scaladsl.SlickSession
import akka.stream.scaladsl.Sink
import cats.implicits.toTraverseOps
import models.company.CompanySync
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.company.CompanySyncRepositoryInterface
import repositories.company.CompanyTable
import tasks.computeStartingTime

import java.time.LocalTime
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import utils.Logs.RichLogger

class CompanyUpdateTask(
    actorSystem: ActorSystem,
    companyRepository: CompanyRepositoryInterface,
    companySyncService: CompanySyncServiceInterface,
    companySyncRepository: CompanySyncRepositoryInterface
)(implicit
    executionContext: ExecutionContext,
    materializer: Materializer
) {

  implicit val session: SlickSession = SlickSession.forConfig("slick.dbs.default")

  actorSystem.registerOnTermination(() => session.close())

  import session.profile.api._

  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  def schedule(): Cancellable = {
    val initialDelay = computeStartingTime(LocalTime.of(2, 0))

    actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = 1.days) { () =>
      logger.info("Starting CompanyUpdateTask")
      runTask(): Unit
    }
  }

  // Be carefull on how much stress you can put to the database, database task are queued into 1000 slot queue.
  // If more tasks are pushed than what the database can handle, it could result to RejectionException thus rejecting any call to database
  def runTask(): Future[Unit] = for {
    companySync <- getCompanySync()
    res <- Slick
      .source(CompanyTable.table.result)
      .grouped(300)
      .throttle(1, 1.second)
      .mapAsync(1) { companies =>
        logger.infoWithTitle(
          "company_update_task_item",
          s"Syncing ${companies.size} companies"
        )
        companySyncService.syncCompanies(companies, companySync.lastUpdated)
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
      } else Future.successful(())
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
