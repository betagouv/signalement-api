package controllers

import models.PaginatedResult.paginatedResultWrites
import models._
import models.company.CompanyAddressUpdate
import models.company.CompanyCreation
import models.company.CompanyRegisteredSearch
import models.company.CompanyWithNbReports
import models.company.UndeliveredDocument
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import repositories.company.CompanyRepositoryInterface
import utils.SIRET
import utils.auth.Authenticator
import utils.auth.UserAction.WithPermission
import utils.auth.UserAction.WithRole

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyController(
    companyOrchestrator: CompanyOrchestrator,
    val companyVisibilityOrch: CompaniesVisibilityOrchestrator,
    val companyRepository: CompanyRepositoryInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseCompanyController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def fetchHosts(companyId: UUID) = SecuredAction.andThen(WithRole(UserRole.Admin, UserRole.DGCCRF)).async {
    companyOrchestrator.fetchHosts(companyId).map(x => Ok(Json.toJson(x)))
  }

  def create() =
    SecuredAction.andThen(WithPermission(UserPermission.updateCompany)).async(parse.json) { implicit request =>
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

  def searchRegistered() = SecuredAction.andThen(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { request =>
    CompanyRegisteredSearch
      .fromQueryString(request.queryString)
      .flatMap(filters => PaginatedSearch.fromQueryString(request.queryString).map((filters, _)))
      .fold(
        error => {
          logger.error("Cannot parse querystring" + request.queryString, error)
          Future.successful(BadRequest)
        },
        filters =>
          companyOrchestrator
            .searchRegistered(filters._1, filters._2, request.identity)
            .map(res => Ok(Json.toJson(res)(paginatedResultWrites[CompanyWithNbReports])))
      )
  }

  def searchById(companyId: UUID) = SecuredAction.async { request =>
    companyOrchestrator
      .searchRegisteredById(companyId, request.identity)
      .map(res => Ok(Json.toJson(res)(paginatedResultWrites[CompanyWithNbReports])))

  }

  def searchCompanyByWebsite(url: String) = Action.async { _ =>
    companyOrchestrator
      .searchCompanyByWebsite(url)
      .map(results => Ok(Json.toJson(results)))
  }

  def searchCompanyOrSimilarWebsite(url: String) = Action.async { _ =>
    companyOrchestrator
      .searchSimilarCompanyByWebsite(url)
      .map(results => Ok(Json.toJson(results)))
  }

  def getResponseRate(companyId: UUID) = SecuredAction.async { request =>
    companyOrchestrator
      .getCompanyResponseRate(companyId, request.identity.userRole)
      .map(results => Ok(Json.toJson(results)))
  }

  def companiesToActivate() = SecuredAction.andThen(WithRole(UserRole.Admin)).async { _ =>
    companyOrchestrator
      .companiesToActivate()
      .map(result => Ok(Json.toJson(result)))
  }

  def inactiveCompanies() = Action.async { _ =>
    companyOrchestrator.getInactiveCompanies
      .map(_.sortBy(_.ignoredReportCount)(Ordering.Int.reverse))
      .map(result => Ok(Json.toJson(result)))
  }

  def visibleCompanies() = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
    companyVisibilityOrch
      .fetchVisibleCompanies(request.identity)
      .map(x => Ok(Json.toJson(x)))
  }

  def getActivationDocument() =
    SecuredAction.andThen(WithPermission(UserPermission.editDocuments)).async(parse.json) { implicit request =>
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
    SecuredAction.andThen(WithPermission(UserPermission.editDocuments)).async(parse.json) { implicit request =>
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
    SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
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

  def confirmFollowUp() = SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
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
    SecuredAction.andThen(WithPermission(UserPermission.updateCompany)).async(parse.json) { implicit request =>
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

  def handleUndeliveredDocument(siret: String) =
    SecuredAction.andThen(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
      request.body
        .validate[UndeliveredDocument]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          undeliveredDocument =>
            companyOrchestrator
              .handleUndeliveredDocument(SIRET.fromUnsafe(siret), request.identity.id, undeliveredDocument)
              .map(
                _.map(e => Ok(Json.toJson(e)))
                  .getOrElse(NotFound)
              )
        )
    }
}

object CompanyObjects {
  case class CompanyList(companyIds: List[UUID])
}
