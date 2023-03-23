package tasks.company

import config.CompanyUpdateTaskConfiguration
import models.company.Company
import play.api.Logger
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson.asJson
import sttp.client3.playJson.playJsonBodySerializer
import sttp.model.Header
import utils.SIRET

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait CompanySyncServiceInterface {
  def syncCompanies(companies: Seq[Company], lastUpdated: OffsetDateTime): Future[List[CompanySearchResult]]
  def companyBySiret(siret: SIRET): Future[Option[CompanySearchResult]]
}

class CompanySyncService(companyUpdateConfiguration: CompanyUpdateTaskConfiguration)(implicit
    executionContext: ExecutionContext
) extends CompanySyncServiceInterface {
  val logger: Logger = Logger(this.getClass)

  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  override def syncCompanies(
      companies: Seq[Company],
      lastUpdated: OffsetDateTime
  ): Future[List[CompanySearchResult]] = {

    val request = basicRequest
      .headers(Header("X-Api-Key", companyUpdateConfiguration.etablissementApiKey))
      .post(
        uri"${companyUpdateConfiguration.etablissementApiUrl}"
          .addParam("lastUpdated", Some(lastUpdated.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
      )
      .body(companies.map(_.siret))
      .response(asJson[List[CompanySearchResult]])

    // logger.trace(request.toCurl)

    val response =
      request.send(backend)
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

  override def companyBySiret(siret: SIRET): Future[Option[CompanySearchResult]] = {
    val request = basicRequest
      .headers(Header("X-Api-Key", companyUpdateConfiguration.etablissementApiKey))
      .post(uri"${companyUpdateConfiguration.etablissementApiUrl}")
      .body(List(siret))
      .response(asJson[List[CompanySearchResult]])

    val response =
      request.send(backend)
    response
      .map(_.body)
      .map {
        case Right(companyList) =>
          companyList.headOption.map { companySearchResult =>
            companySearchResult
          }
        case Left(value) =>
          logger.warn("Error calling company update", value)
          Option.empty
      }
  }
}
