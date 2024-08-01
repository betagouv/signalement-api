package utils

import models.company.Address
import models.company.Company
import tasks.company.CompanySearchResult
import tasks.company.CompanySyncServiceInterface

import java.time.OffsetDateTime
import scala.concurrent.Future

class CompanySyncServiceMock extends CompanySyncServiceInterface {
  override def syncCompanies(companies: Seq[Company], lastUpdated: OffsetDateTime): Future[List[CompanySearchResult]] =
    Future.successful(List.empty[CompanySearchResult])

  override def companyBySiret(siret: SIRET): Future[Option[CompanySearchResult]] = Future.successful(
    Some(
      CompanySearchResult(
        siret = siret,
        name = None,
        commercialName = None,
        establishmentCommercialName = None,
        brand = None,
        isHeadOffice = false,
        address = Address(),
        activityCode = None,
        activityLabel = None,
        isMarketPlace = false,
        isOpen = false,
        isPublic = false,
        lastUpdated = None
      )
    )
  )

  override def companiesBySirets(sirets: List[SIRET]): Future[List[CompanySearchResult]] = Future.successful(List.empty)

  override def companyBySiren(siren: SIREN, onlyHeadOffice: Boolean): Future[List[CompanySearchResult]] =
    Future.successful(List.empty)
}
