package controllers

import authentication.Authenticator
import models.PaginatedResult.paginatedResultWrites
import models._
import models.company.CompanyAddressUpdate
import models.company.CompanyCreation
import models.company.CompanyRegisteredSearch
import models.company.CompanySort
import models.company.CompanyWithNbReports
import orchestrators.AlbertOrchestrator
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.ControllerComponents

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class CompanyController(
    val companyOrchestrator: CompanyOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    albertOrchestrator: AlbertOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseCompanyController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def fetchHosts(companyId: UUID) = Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async {
    companyOrchestrator.fetchHosts(companyId).map(x => Ok(Json.toJson(x)))
  }

  def fetchPhones(companyId: UUID) = Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async {
    companyOrchestrator.fetchPhones(companyId).map(x => Ok(Json.toJson(x)))
  }

  def manuallyRegisterCompany() =
    Act.secured.admins.async(parse.json) { implicit request =>
      request.body
        .validate[CompanyCreation]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          companyCreation =>
            companyOrchestrator
              .create(companyCreation)
              .map(company => Ok(Json.toJson(company)))
        )
    }

  def searchCompanies() = Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async { request =>
    implicit val userRole: Option[UserRole] = Some(request.identity.userRole)

    (for {
      filters <- CompanyRegisteredSearch.fromQueryString(request.queryString)
      search  <- PaginatedSearch.fromQueryString(request.queryString)
    } yield {
      val sort = CompanySort.fromQueryString(request.queryString)
      companyOrchestrator
        .searchRegistered(filters, search, sort, request.identity)
        .map(res => Ok(Json.toJson(res)(paginatedResultWrites[CompanyWithNbReports])))
    }) match {
      case Success(future) => future
      case Failure(error) =>
        logger.error("Cannot parse querystring" + request.queryString, error)
        Future.successful(BadRequest)
    }
  }

  def getCompany(companyId: UUID) = Act.securedWithCompanyAccessById(companyId).allowImpersonation.async { request =>
    implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
    companyOrchestrator
      .searchRegisteredById(companyId, request.identity)
      .map(res => Ok(Json.toJson(res)(paginatedResultWrites[CompanyWithNbReports])))

  }

  def searchCompanyOrSimilarWebsite(url: String) = Act.public.standardLimit.async { _ =>
    companyOrchestrator
      .searchSimilarCompanyByWebsite(url)
      .map(results => Ok(Json.toJson(results)))
  }

  def getResponseRate(companyId: UUID) =
    Act.securedWithCompanyAccessById(companyId).allowImpersonation.async { request =>
      companyOrchestrator
        .getCompanyResponseRate(companyId, request.identity)
        .map(results => Ok(Json.toJson(results)))
    }

  def companiesToActivate() = Act.secured.adminsAndReadonly.async { _ =>
    companyOrchestrator
      .companiesToActivate()
      .map(result => Ok(Json.toJson(result)))
  }

  def inactiveCompanies() = Act.secured.adminsAndReadonly.async { _ =>
    companyOrchestrator.getInactiveCompanies
      .map(_.sortBy(_.ignoredReportCount)(Ordering.Int.reverse))
      .map(result => Ok(Json.toJson(result)))
  }

  def getCompaniesOfPro() = Act.secured.pros.allowImpersonation.async { implicit request =>
    companiesVisibilityOrchestrator
      .fetchVisibleCompanies(request.identity)
      .map(x => Ok(Json.toJson(x)))
  }

  def getActivationDocument() =
    Act.secured.adminsAndReadonly.async(parse.json) { implicit request =>
      import CompanyObjects.CompanyList
      request.body
        .validate[CompanyList](Json.reads[CompanyList])
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          results =>
            companyOrchestrator
              .getActivationDocument(results.companyIds)
              .map {
                case Some(pdfSource) =>
                  Ok.chunked(
                    content = pdfSource,
                    inline = false,
                    fileName = Some(s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.pdf")
                  )
                case None =>
                  NotFound
              }
        )
    }

  def getFollowUpDocument() =
    Act.secured.adminsAndReadonly.async(parse.json) { implicit request =>
      import CompanyObjects.CompanyList
      request.body
        .validate[CompanyList](Json.reads[CompanyList])
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          results =>
            companyOrchestrator
              .getFollowUpDocument(results.companyIds, request.identity.id)
              .map {
                case Some(pdfSource) =>
                  Ok.chunked(
                    content = pdfSource,
                    inline = false,
                    fileName = Some(s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.pdf")
                  )
                case None =>
                  NotFound
              }
        )
    }

  def confirmContactByPostOnCompanyList() =
    Act.secured.admins.async(parse.json) { implicit request =>
      import CompanyObjects.CompanyList
      request.body
        .validate[CompanyList](Json.reads[CompanyList])
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          companyList =>
            companyOrchestrator
              .confirmContactByPostOnCompanyList(companyList, request.identity.id)
              .map(_ => Ok)
        )
    }

  def confirmFollowUp() = Act.secured.admins.async(parse.json) { implicit request =>
    import CompanyObjects.CompanyList
    request.body
      .validate[CompanyList](Json.reads[CompanyList])
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        companyList =>
          companyOrchestrator
            .confirmFollowUp(companyList, request.identity.id)
            .map(_ => Ok)
      )
  }

  def updateCompanyAddress(id: UUID) =
    Act.secured.admins.async(parse.json) { implicit request =>
      request.body
        .validate[CompanyAddressUpdate]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          companyAddressUpdate =>
            companyOrchestrator
              .updateCompanyAddress(id, request.identity.id, companyAddressUpdate)
              .map {
                _.map(c => Ok(Json.toJson(c)))
                  .getOrElse(NotFound)
              }
        )
    }

  def getProblemsSeenByAlbert(id: UUID) = Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async {
    for {
      maybeResult <- albertOrchestrator.genProblemsForCompany(id)
    } yield Ok(Json.toJson(maybeResult))
  }

}

object CompanyObjects {
  case class CompanyList(companyIds: List[UUID])
}
