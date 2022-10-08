package utils

import company.CompanySearchResult
import models.company.Company
import tasks.company.CompanySyncServiceInterface

import scala.concurrent.Future

class CompanySyncServiceMock extends CompanySyncServiceInterface {
  override def syncCompanies(companies: Seq[Company]): Future[List[CompanySearchResult]] =
    Future.successful(List.empty[CompanySearchResult])
}
