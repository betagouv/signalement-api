package company

import com.mohiva.play.silhouette.api.Silhouette
import company.companydata.CompanyDataRepositoryInterface
import controllers.BaseController
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv
import utils.FrontRoute
import utils.SIREN
import utils.SIRET

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyDataController(
    val companyDataRepository: CompanyDataRepositoryInterface,
    val silhouette: Silhouette[AuthEnv],
    val frontRoute: FrontRoute,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def searchCompany(q: String, postalCode: String) = UnsecuredAction.async { _ =>
    logger.debug(s"searchCompany $postalCode $q")
    search(q, postalCode)
      .map(results => Ok(Json.toJson(results)))
  }

  def searchCompanyByIdentity(identity: String) = UnsecuredAction.async { _ =>
    logger.debug(s"searchCompanyByIdentity $identity")
    searchByIdentity(identity)
      .map(res => Ok(Json.toJson(res)))
  }

  private def search(q: String, postalCode: String): Future[List[CompanySearchResult]] = {
    logger.debug(s"searchCompany $postalCode $q")
    companyDataRepository
      .search(q, postalCode)
      .map(results => results.map(result => result._1.toSearchResult(result._2.map(_.label))))
  }

  private def searchByIdentity(identity: String): Future[List[CompanySearchResult]] = {
    logger.debug(s"searchCompanyByIdentity $identity")

    (identity.replaceAll("\\s", "") match {
      case q if q.matches(SIRET.pattern) =>
        companyDataRepository.searchBySiretIncludingHeadOfficeWithActivity(SIRET.fromUnsafe(q))
      case q =>
        SIREN.pattern.r
          .findFirstIn(q)
          .map(siren =>
            for {
              headOffice <- companyDataRepository.searchHeadOfficeBySiren(SIREN(siren))
              companies <- headOffice
                .map(company => Future(List(company)))
                .getOrElse(companyDataRepository.searchBySiren(SIREN(siren)))
            } yield companies
          )
          .getOrElse(Future(List.empty))
    }).map(companiesWithActivity =>
      companiesWithActivity.map { case (company, activity) =>
        company.toSearchResult(activity.map(_.label))
      }
    )
  }

}

object CompanyObjects {
  case class CompanyList(companyIds: List[UUID])
}
