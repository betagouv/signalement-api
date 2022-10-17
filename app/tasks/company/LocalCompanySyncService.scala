package tasks.company

import company.CompanySearchResult
import company.companydata.CompanyDataRepositoryInterface
import models.company.Company
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait LocalCompanySyncServiceInterface {
  def syncCompanies(companies: Seq[Company]): Future[List[CompanySearchResult]]
}

@Deprecated(since = "use CompanySyncService.syncCompanies, call should be done using asynchronous call")
class LocalCompanySyncService(
    companyDataRepository: CompanyDataRepositoryInterface
)(implicit
    executionContext: ExecutionContext
) extends LocalCompanySyncServiceInterface {
  val logger: Logger = Logger(this.getClass)

  override def syncCompanies(companies: Seq[Company]): Future[List[CompanySearchResult]] =
    companyDataRepository
      .searchBySirets(companies.map(_.siret).toList, includeClosed = true)
      .map(companies =>
        companies.map { case (companyData, maybeActivity) =>
          companyData.toSearchResult(maybeActivity.map(_.label))
        }
      )

}
