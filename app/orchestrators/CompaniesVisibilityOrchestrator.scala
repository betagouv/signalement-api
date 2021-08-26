package orchestrators

import javax.inject.Inject
import models.CompanyData
import models.User
import models.UserRoles
import repositories._
import utils.SIREN
import utils.SIRET

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class SiretsSirens(sirens: List[SIREN], sirets: List[SIRET]) {
  def toList() = sirens.map(_.value).concat(sirets.map(_.value)).distinct
}

class CompaniesVisibilityOrchestrator @Inject() (
    companyDataRepo: CompanyDataRepository,
    companyRepo: CompanyRepository
)(implicit val executionContext: ExecutionContext) {

  def fetchVisibleCompanies(pro: User): Future[List[CompanyData]] =
    (for {
      authorizedSirets <- companyRepo.fetchCompaniesWithLevel(pro).map(_.map(_._1.siret))
      headOfficeSirets <- companyDataRepo.searchHeadOffices(authorizedSirets)
      authorizedHeadOffices = authorizedSirets.intersect(headOfficeSirets)
      authorizedSubcompanies = authorizedSirets.diff(headOfficeSirets)
      companiesForHeadOffices <-
        companyDataRepo.searchBySirens(authorizedHeadOffices.map(SIREN.apply), includeClosed = true)
      companiesWithoutHeadOffice <- companyDataRepo.searchBySirets(authorizedSubcompanies, includeClosed = true)
    } yield companiesForHeadOffices.concat(companiesWithoutHeadOffice).map(_._1).distinct)
      .flatMap(filterReportedCompanyData)

  private[this] def filterReportedCompanyData(companies: List[CompanyData]): Future[List[CompanyData]] =
    for {
      reportedCompaniesSiret <- companyRepo.findBySirets(companies.map(_.siret)).map(_.map(_.siret))
    } yield companies.filter(x => reportedCompaniesSiret.contains(x.siret))

  def fetchVisibleSiretsSirens(user: User): Future[SiretsSirens] =
    for {
      authorizedSirets <- companyRepo.fetchCompaniesWithLevel(user).map(_.map(_._1.siret))
      authorizedHeadofficeSirens <- companyDataRepo
                                      .searchBySirets(authorizedSirets, includeClosed = true)
                                      .map(companies =>
                                        companies
                                          .map(_._1)
                                          .filter(_.etablissementSiege.contains("true"))
                                          .map(_.siren)
                                      )
    } yield removeRedundantSirets(SiretsSirens(authorizedHeadofficeSirens, authorizedSirets))

  def filterUnauthorizedSiretSirenList(siretSirenList: List[String], user: User): Future[List[String]] =
    if (user.userRole == UserRoles.Pro) {
      val formattedSiretsSirens = formatSiretSirenList(siretSirenList)
      fetchVisibleSiretsSirens(user).map { allowed =>
        val filteredSiretsSirens = SiretsSirens(
          sirets = formattedSiretsSirens.sirets.filter(wanted =>
            allowed.sirens.contains(SIREN(wanted)) || allowed.sirets.contains(wanted)
          ),
          sirens = allowed.sirens.intersect(formattedSiretsSirens.sirens)
        ).toList()
        if (filteredSiretsSirens.isEmpty) {
          allowed.toList()
        } else {
          filteredSiretsSirens
        }
      }
    } else {
      Future(siretSirenList)
    }

  private[this] def removeRedundantSirets(id: SiretsSirens): SiretsSirens =
    SiretsSirens(
      id.sirens,
      id.sirets.filter(siret => !id.sirens.contains(SIREN(siret)))
    )

  private[this] def formatSiretSirenList(siretSirenList: List[String]): SiretsSirens =
    SiretsSirens(
      sirens = siretSirenList.filter(SIREN.isValid).map(SIREN.apply),
      sirets = siretSirenList.filter(SIRET.isValid).map(SIRET.apply)
    )
}
