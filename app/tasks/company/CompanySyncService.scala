package tasks.company

import company.CompanySearchResult
import config.CompanyUpdateTaskConfiguration
import models.Company
import play.api.Logger
import sttp.capabilities
import sttp.client3.HttpClientFutureBackend
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson.asJson
import sttp.client3.playJson.playJsonBodySerializer
import sttp.model.Header

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait CompanySyncServiceInterface {
  def syncCompanies(companies: Seq[Company]): Future[List[CompanySearchResult]]
}

class CompanySyncService(companyUpdateConfiguration: CompanyUpdateTaskConfiguration)(implicit
    executionContext: ExecutionContext
) extends CompanySyncServiceInterface {
  val logger: Logger = Logger(this.getClass)

  private val backend: SttpBackend[Future, capabilities.WebSockets] = HttpClientFutureBackend()

  override def syncCompanies(companies: Seq[Company]): Future[List[CompanySearchResult]] = {

    val request = basicRequest
      .headers(Header("X-Api-Key", companyUpdateConfiguration.etablissementApiKey))
      .post(uri"${companyUpdateConfiguration.etablissementApiUrl}")
      .body(companies.map(_.siret))
      .response(asJson[List[CompanySearchResult]])

    logger.debug(request.toCurl)

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

}
