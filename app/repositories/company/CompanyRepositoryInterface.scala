package repositories.company

import com.google.inject.ImplementedBy
import models.Company
import models.CompanyRegisteredSearch
import models.PaginatedResult
import models.PaginatedSearch
import models.UserRole
import repositories.CRUDRepositoryInterface
import utils.SIREN
import utils.SIRET

import java.util.UUID
import scala.concurrent.Future
@ImplementedBy(classOf[CompanyRepository])
trait CompanyRepositoryInterface extends CRUDRepositoryInterface[Company] {

  def searchWithReportsCount(
      search: CompanyRegisteredSearch,
      paginate: PaginatedSearch,
      userRole: UserRole
  ): Future[PaginatedResult[(Company, Int, Int)]]

  def getOrCreate(siret: SIRET, data: Company): Future[Company]

  def fetchCompanies(companyIds: List[UUID]): Future[List[Company]]

  def findBySiret(siret: SIRET): Future[Option[Company]]

  def findBySirets(sirets: List[SIRET]): Future[List[Company]]

  def findByName(name: String): Future[List[Company]]

  def findBySiren(siren: List[SIREN]): Future[List[Company]]
}