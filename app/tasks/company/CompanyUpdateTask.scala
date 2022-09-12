package tasks.company

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.alpakka.slick.scaladsl.SlickSession
import company.CompanySearchResult
import company.companydata.CompanyDataRepositoryInterface
import config.CompanyUpdateTaskConfiguration
import models.Company
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.company.CompanyTable
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson.asJson
import sttp.client3.playJson.playJsonBodySerializer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class CompanyUpdateTask(
    actorSystem: ActorSystem,
    companyUpdateConfiguration: CompanyUpdateTaskConfiguration,
    companyRepository: CompanyRepositoryInterface,
    companyDataRepository: CompanyDataRepositoryInterface
)(implicit
    executionContext: ExecutionContext,
    materializer: Materializer
) {

  implicit val session = SlickSession.forConfig("slick.dbs.default")
  val batchSize = 5000
  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  actorSystem.registerOnTermination(() => session.close())

  import session.profile.api._
  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds
  val startTime = companyUpdateConfiguration.startTime

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 10.minutes, interval = 1.hour) { () =>
    runTask()
    ()
  }

  def runTask() =
    Slick
      .source(CompanyTable.table.result)
      .grouped(500)
      .mapAsync(1) { companies =>
        if (companyUpdateConfiguration.localSync) {
          syncCompaniesLocally(companies)
        } else {
          syncCompanies(companies)
        }
      }
      .map((companies: Seq[CompanySearchResult]) =>
        companies.map(c => companyRepository.updateBySiret(c.siret, c.isOpen, c.isHeadOffice))
      )
      .log("company update")
      .run()
      .map(_ => logger.info("Company update done"))

  private def syncCompanies(companies: Seq[Company]): Future[List[CompanySearchResult]] = {
    val response =
      basicRequest
        .post(uri"http://localhost:9001/api/companies/search")
        .body(companies.map(_.siret))
        .response(asJson[List[CompanySearchResult]])
        .send(backend)
    response
      .map(_.body)
      .map {
        case Right(companyList) =>
          companyList.map { companySearchResult =>
            companySearchResult
          }
        case Left(value) =>
          logger.warn("Error calling company update", value)
          List.empty
      }
  }

  @Deprecated(since = "use syncCompanies, call should be done using asynchronous call")
  private def syncCompaniesLocally(companies: Seq[Company]): Future[List[CompanySearchResult]] =
    companyDataRepository
      .searchBySirets(companies.map(_.siret).toList, includeClosed = true)
      .map(companies =>
        companies.map { case (companyData, maybeActivity) =>
          companyData.toSearchResult(maybeActivity.map(_.label))
        }
      )
}
