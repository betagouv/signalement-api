package tasks.company

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.alpakka.slick.scaladsl.SlickSession
import company.CompanySearchResult
import config.TaskConfiguration
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.company.CompanyTable
import tasks.computeStartingTime

import java.time.LocalTime
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class CompanyUpdateTask(
    actorSystem: ActorSystem,
    companyRepository: CompanyRepositoryInterface,
    taskConfiguration: TaskConfiguration,
    companySyncService: CompanySyncServiceInterface,
    localCompanySyncService: LocalCompanySyncServiceInterface
)(implicit
    executionContext: ExecutionContext,
    materializer: Materializer
) {

  implicit val session = SlickSession.forConfig("slick.dbs.default")
  val batchSize = 5000

  actorSystem.registerOnTermination(() => session.close())

  import session.profile.api._
  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  val initialDelay = computeStartingTime(LocalTime.of(2, 0))

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = 1.days) { () =>
    logger.warn("Starting CompanyUpdateTask")
    if (taskConfiguration.active) {
      runTask()
    }
    ()
  }

  def runTask() =
    Slick
      .source(CompanyTable.table.result)
      .grouped(500)
      .mapAsync(1) { companies =>
        if (taskConfiguration.companyUpdate.localSync) {
          localCompanySyncService.syncCompanies(companies)
        } else {
          companySyncService.syncCompanies(companies)
        }
      }
      .map((companies: Seq[CompanySearchResult]) =>
        companies.map(c =>
          companyRepository.updateBySiret(
            c.siret,
            c.isOpen,
            c.isHeadOffice,
            c.isPublic,
            c.address.number,
            c.address.street,
            c.address.addressSupplement
          )
        )
      )
      .log("company update")
      .run()
      .map(_ => logger.info("Company update done"))

}
