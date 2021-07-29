package controllers

import javax.inject.Inject
import javax.inject.Singleton
import play.api.mvc.InjectedController
import repositories.CompanyDataRepository
import repositories.CompanyRepository
import repositories.ReportRepository
import utils.SIRET

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class MigrateAddressController @Inject() (
    val companyRepository: CompanyRepository,
    val companyDataRepository: CompanyDataRepository,
    val reportRepository: ReportRepository
)(implicit ec: ExecutionContext)
    extends InjectedController {

  def run() = Action {
    loop()
    Ok("ok")
  }

  def loop(): Future[Any] =
    companyRepository.migration_getTodoSIRET().map { siretOpt =>
      siretOpt.map { siret =>
        companyDataRepository.searchBySiret(SIRET(siret), includeClosed = true).map { dataOpt =>
          val companyData = dataOpt.headOption.map(_._1)
          companyRepository.migration_update(siret, companyData)
          if (companyData.isEmpty) {
            println(s"[MigrateAddressController] missing etablissement $siret")
          }
          loop()
        }
      }
    }
}
