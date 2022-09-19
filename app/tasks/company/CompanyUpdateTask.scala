package tasks.company

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.alpakka.slick.scaladsl.SlickSession
import company.CompanySearchResult
import config.CompanyUpdateTaskConfiguration
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.company.CompanyTable

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class CompanyUpdateTask(
    actorSystem: ActorSystem,
    companyRepository: CompanyRepositoryInterface,
    companyUpdateConfiguration: CompanyUpdateTaskConfiguration,
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

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 10.minutes, interval = 1.hour) { () =>
    logger.debug("Starting CompanyUpdateTask")
    runTask()
    ()
  }

  def runTask() =
    Slick
      .source(CompanyTable.table.result)
      .grouped(500)
      .mapAsync(1) { companies =>
        if (companyUpdateConfiguration.localSync) {
          localCompanySyncService.syncCompanies(companies)
        } else {
          companySyncService.syncCompanies(companies)
        }
      }
      .map((companies: Seq[CompanySearchResult]) =>
        companies.map(c => companyRepository.updateBySiret(c.siret, c.isOpen, c.isHeadOffice))
      )
      .log("company update")
      .run()
      .map(_ => logger.info("Company update done"))

}
