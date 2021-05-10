package orchestrators

import javax.inject.Inject
import models.{CompanyData, User, UserRoles}
import repositories._
import utils.{SIREN, SIRET}

import scala.concurrent.{ExecutionContext, Future}

case class SiretsSirens(sirens: List[SIREN], sirets: List[SIRET]) {
  def toList() = sirens.map(_.value).union(sirets.map(_.value))
}

class CompaniesVisibilityOrchestrator @Inject()(
  companyDataRepo: CompanyDataRepository,
  companyRepo: CompanyRepository,
)(implicit val executionContext: ExecutionContext) {

  def fetchViewableCompanies(pro: User): Future[List[CompanyData]] = {
    for {
      authorizedSirets <- companyRepo.fetchCompaniesWithLevel(pro).map(_.map(_._1.siret))
      headOfficeSirets <- companyDataRepo.searchHeadOffices(authorizedSirets)
      companiesForHeadOffices <- companyDataRepo.searchBySirens(authorizedSirets.intersect(headOfficeSirets).map(SIREN.apply), includeClosed = true)
      companiesWithoutHeadOffice <- companyDataRepo.searchBySirets(authorizedSirets.diff(headOfficeSirets), includeClosed = true)
    } yield {
      companiesForHeadOffices.union(companiesWithoutHeadOffice).map(_._1).distinct
    }
  }

  def fetchViewableSiretsSirens(user: User): Future[SiretsSirens] = {
    for {
      authorizedSirets <- companyRepo.fetchCompaniesWithLevel(user).map(_.map(_._1.siret))
      authorizedHeadofficeSirens <- companyDataRepo.searchBySirets(authorizedSirets, includeClosed = true)
        .map(companies => companies
          .map(_._1)
          .filter(_.etablissementSiege.contains("true"))
          .map(_.siren)
        )
    } yield {
      removeRedundantSirets(SiretsSirens(authorizedHeadofficeSirens, authorizedSirets))
    }
  }

  def filterUnauthorizedSiretSirenList(siretSirenList: List[String], user: User): Future[List[String]] = {
    if (user.userRole == UserRoles.Pro) {
      val formattedSiretsSirens = formatSiretSirenList(siretSirenList)
      fetchViewableSiretsSirens(user).map(allowed => {
        val filteredSiretsSirens = SiretsSirens(
          sirets = formattedSiretsSirens.sirets.filter(wanted => allowed.sirens.contains(SIREN(wanted)) || allowed.sirets.contains(wanted)),
          sirens = allowed.sirens.intersect(formattedSiretsSirens.sirens),
        ).toList()
        if (filteredSiretsSirens.isEmpty) {
          allowed.toList()
        } else {
          filteredSiretsSirens
        }
      })
    } else {
      Future(siretSirenList)
    }
  }

  private[this] def removeRedundantSirets(id: SiretsSirens): SiretsSirens = {
    SiretsSirens(
      id.sirens,
      id.sirets.filter(siret => !id.sirens.contains(SIREN(siret)))
    )
  }

  private[this] def formatSiretSirenList(siretSirenList: List[String]): SiretsSirens = {
    SiretsSirens(
      sirens = siretSirenList.filter(SIREN.isValid).map(SIREN.apply),
      sirets = siretSirenList.filter(SIRET.isValid).map(SIRET.apply)
    )
  }
}
