package utils

import models.PaginatedResult
import models.PaginatedSearch
import models.User
import models.company.Company
import models.company.CompanyRegisteredSearch
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import repositories.company.CompanyRepositoryInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future

class CompanyRepositoryMock(database: mutable.Map[UUID, Company] = mutable.Map.empty[UUID, Company])
    extends CRUDRepositoryMock[Company](database, _.id)
    with CompanyRepositoryInterface {

  override def searchWithReportsCount(
      search: CompanyRegisteredSearch,
      paginate: PaginatedSearch,
      user: User
  ): Future[PaginatedResult[(Company, Int, Int)]] = ???

  override def getOrCreate(siret: SIRET, data: Company): Future[Company] = ???

  override def fetchCompanies(companyIds: List[UUID]): Future[List[Company]] = ???

  override def findBySiret(siret: SIRET): Future[Option[Company]] =
    Future.successful(database.find(_._2.siret == siret).map(_._2))

  override def findCompanyAndHeadOffice(siret: SIRET): Future[List[Company]] = ???

  override def findHeadOffices(siren: List[SIREN], openOnly: Boolean): Future[List[Company]] = ???

  override def findBySirets(sirets: List[SIRET]): Future[List[Company]] = ???

  override def findByName(name: String): Future[List[Company]] = ???

  override def findBySiren(siren: List[SIREN]): Future[List[Company]] = ???

  override def findWithOutdatedAlbertActivityLabel(outdatedCutoffDate: OffsetDateTime, limit: Int) = ???

  override def updateBySiret(
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
  ): Future[SIRET] = ???

  override def getInactiveCompanies: Future[List[(Company, Int)]] = ???

  override def streamCompanies: Source[Company, NotUsed] = ???
}
