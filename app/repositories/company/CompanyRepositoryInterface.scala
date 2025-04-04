package repositories.company

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import models.PaginatedResult
import models.PaginatedSearch
import models.User
import models.company.Company
import models.company.CompanyRegisteredSearch
import models.company.CompanySort
import repositories.CRUDRepositoryInterface
import utils.Country
import utils.SIREN
import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

trait CompanyRepositoryInterface extends CRUDRepositoryInterface[Company] {

  def streamCompanies: Source[Company, NotUsed]

  def searchWithReportsCount(
      search: CompanyRegisteredSearch,
      paginate: PaginatedSearch,
      sort: Option[CompanySort],
      user: User
  ): Future[PaginatedResult[(Company, Long, Float)]]

  def getOrCreate(siret: SIRET, data: Company): Future[Company]

  def fetchCompanies(companyIds: List[UUID]): Future[List[Company]]

  def findBySiret(siret: SIRET): Future[Option[Company]]

  def findCompanyAndHeadOffice(siret: SIRET): Future[List[Company]]

  def findHeadOffices(siren: List[SIREN], openOnly: Boolean): Future[List[Company]]

  def findBySirets(sirets: List[SIRET]): Future[List[Company]]

  def findByName(name: String): Future[List[Company]]

  def findBySiren(siren: List[SIREN]): Future[List[Company]]

  def findWithOutdatedAlbertActivityLabel(outdatedCutoffDate: OffsetDateTime, limit: Int): Future[List[Company]]

  def updateBySiret(
      siret: SIRET,
      isOpen: Boolean,
      isHeadOffice: Boolean,
      isPublic: Boolean,
      number: Option[String],
      street: Option[String],
      addressSupplement: Option[String],
      name: String,
      brand: Option[String],
      country: Option[Country]
  ): Future[SIRET]

  def getInactiveCompanies: Future[List[(Company, Int)]]
}
