package orchestrators

import javax.inject.Inject
import models.{CompanyData, User, UserRoles}
import play.api.Environment
import repositories._
import utils.{SIREN, SIRET}

import scala.concurrent.{ExecutionContext, Future}

case class SirensSirets(sirens: List[SIREN], sirets: List[SIRET]) {
  def toList() = sirens.map(_.value).union(sirets.map(_.value))
}

class CompaniesVisibilityOrchestrator @Inject()(
  reportRepository: ReportRepository,
  companyDataRepository: CompanyDataRepository,
  companyRepository: CompanyRepository,
  environment: Environment
)(implicit val executionContext: ExecutionContext) {

  def fetchViewableCompanies(user: User): Future[List[CompanyData]] = {
    for {
      authorizedSirets <- companyRepository.fetchCompaniesWithLevel(user).map(_.map(_._1.siret))
      headOfficeSirets <- companyDataRepository.searchHeadOffices(authorizedSirets)
      companiesForHeadOffices <- Future.sequence(authorizedSirets.intersect(headOfficeSirets)
        .map(s => companyDataRepository.searchBySiren(SIREN(s), includeClosed = true)))
      companiesWithoutHeadOffice <- Future.sequence(authorizedSirets.diff(headOfficeSirets)
        .map(s => companyDataRepository.searchBySiret(s, includeClosed = true)))
    } yield {
      companiesForHeadOffices.union(companiesWithoutHeadOffice).flatMap(_.map(_._1)).distinct
    }
  }

  def fetchViewableSirensSirets(user: User): Future[SirensSirets] = {
    for {
      authorizedSirets <- companyRepository.fetchCompaniesWithLevel(user).map(_.map(_._1.siret))
      authorizedHeadofficeSirens <- companyDataRepository.searchBySirets(authorizedSirets, includeClosed = true)
        .map(companies => companies
          .map(_._1)
          .filter(_.etablissementSiege.contains("true"))
          .map(_.siren)
        )
    } yield {
      removeRedundantSirets(SirensSirets(authorizedHeadofficeSirens, authorizedSirets))
    }
  }

  def filterUnauthorizedSirensSirets(sirensSirets: List[String], user: User): Future[List[String]] = {
    if (user.userRole == UserRoles.Pro) {
      val formattedSirensSirets = formatSirensSiretsList(sirensSirets)
      fetchViewableSirensSirets(user).map(allowed => {
        if (sirensSirets.isEmpty)
          allowed.toList()
        else SirensSirets(
          sirets = formattedSirensSirets.sirets.filter(wanted => allowed.sirens.contains(SIREN(wanted)) || allowed.sirets.contains(wanted)),
          sirens = allowed.sirens.intersect(formattedSirensSirets.sirens),
        ).toList()
      })
    } else {
      Future(sirensSirets)
    }
  }

  private[this] def removeRedundantSirets(id: SirensSirets): SirensSirets = {
    SirensSirets(
      id.sirens,
      id.sirets.filter(siret => !id.sirens.contains(SIREN(siret)))
    )
  }

  private[this] def formatSirensSiretsList(siretsSirens: List[String]): SirensSirets = {
    SirensSirets(
      sirens = siretsSirens.filter(SIREN.isValid).map(SIREN.apply),
      sirets = siretsSirens.filter(SIRET.isValid).map(SIRET.apply)
    )
  }
}
