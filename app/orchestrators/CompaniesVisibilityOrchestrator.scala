package orchestrators

import models.Company
import models.User
import models.UserRoles
import repositories._
import utils.SIREN
import utils.SIRET

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class SiretsSirens(sirens: List[SIREN], sirets: List[SIRET]) {
  def toList() = sirens.map(_.value).concat(sirets.map(_.value)).distinct
}

class CompaniesVisibilityOrchestrator @Inject() (
    companyDataRepo: CompanyDataRepository,
    companyRepo: CompanyRepository
)(implicit val executionContext: ExecutionContext) {

  def fetchAdminsWithHeadOffice(siret: SIRET): Future[List[User]] =
    for {
      companiesDataIncludingHeadOffice <- companyDataRepo.searchBySiretIncludingHeadOffice(siret)
      companies <- companyRepo.findBySirets(companiesDataIncludingHeadOffice.map(_.siret))
      admins <- companyRepo.fetchUsersByCompanies(companies.map(_.id))
    } yield admins

  def fetchAdminsWithHeadOffices(companies: List[(SIRET, UUID)]): Future[Map[UUID, List[User]]] =
    for {
      admins <- companyRepo.fetchAdminsMapByCompany(companies.map(_._2))
      headOfficesCompanyData <- companyDataRepo
                                  .searchHeadOfficeBySiren(companies.map(c => SIREN(c._1)))
                                  .map(_.map(_._1))
      headOfficesCompany <- companyRepo.findBySirets(headOfficesCompanyData.map(_.siret))
      headOfficeAdminsMap <- companyRepo.fetchAdminsMapByCompany(headOfficesCompany.map(_.id))
      mapHeadOfficeIdByCompanyId = companies
                                     .groupBy(_._2)
                                     .view
                                     .mapValues { values =>
                                       val siren = values.headOption.map(x => SIREN(x._1))
                                       headOfficesCompany.find(c => siren.contains(SIREN(c.siret))).map(_.id)
                                     }
                                     .toMap

    } yield admins.map { x =>
      val headOfficeId = mapHeadOfficeIdByCompanyId(x._1)
      val headOfficeAdmins = headOfficeId.map(headOfficeAdminsMap).getOrElse(List())
      (x._1, (x._2 ++ headOfficeAdmins).distinctBy(_.id))
    }

  def fetchVisibleCompanies(pro: User): Future[List[Company]] =
    (for {
      authorizedSirets <- companyRepo.fetchCompaniesWithLevel(pro).map(_.map(_._1.siret))
      headOfficeSirets <- companyDataRepo.searchHeadOffices(authorizedSirets).map(_.map(_.siret))
      authorizedHeadOffices = authorizedSirets.intersect(headOfficeSirets)
      authorizedSubcompanies = authorizedSirets.diff(headOfficeSirets)
      companiesForHeadOffices <-
        companyDataRepo.searchBySirens(authorizedHeadOffices.map(SIREN.apply), includeClosed = true)
      companiesWithoutHeadOffice <- companyDataRepo.searchBySirets(authorizedSubcompanies, includeClosed = true)
    } yield companiesForHeadOffices.concat(companiesWithoutHeadOffice).map(_._1).distinct)
      .map(_.map(_.siret))
      .flatMap(companyRepo.findBySirets)

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
