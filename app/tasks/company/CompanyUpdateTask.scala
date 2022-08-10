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
import sttp.client3._
import sttp.client3.playJson._
import tasks.computeStartingTime

import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class CompanyUpdateTask(
    actorSystem: ActorSystem,
    companyUpdate: CompanyUpdateTaskConfiguration,
    companyRepository: CompanyRepositoryInterface
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

  val startTime = LocalTime.now()
  val initialDelay: FiniteDuration = computeStartingTime(startTime)

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = 1.day) { () =>
    logger.debug(s"initialDelay - ${initialDelay}");
    runTask(
      OffsetDateTime
        .now(ZoneOffset.UTC)
    )
    ()
  }

  def runTask(now: OffsetDateTime) = {

    println(s"------------------ now = ${now} ------------------")

    val backend = HttpClientFutureBackend()

    Slick
      .source(CompanyTable.table.result)
      .grouped(500)
      .mapAsync(1) { companies =>
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
      .map(companies => companies.map(c => companyRepository.updateBySiret(c.siret, c.isOpen, c.isHeadOffice)))
      .log("company update")
      .run()
      .map(_ => println("DONE"))

  }
}
