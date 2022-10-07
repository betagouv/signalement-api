package controllers

import com.mohiva.play.silhouette.api.Silhouette
import config.EmailConfiguration
import config.TaskConfiguration
import models.PaginatedResult.paginatedResultWrites
import models._
import models.event.Event
import models.report.Report
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.PDFService
import utils.Constants.ActionEvent
import utils.FrontRoute
import utils.SIRET
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission
import utils.silhouette.auth.WithRole

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyController(
    val companyOrchestrator: CompanyOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    val companyRepository: CompanyRepositoryInterface,
    val accessTokenRepository: AccessTokenRepositoryInterface,
    val eventRepository: EventRepositoryInterface,
    val reportRepository: ReportRepositoryInterface,
    val pdfService: PDFService,
    val silhouette: Silhouette[AuthEnv],
    val companyVisibilityOrch: CompaniesVisibilityOrchestrator,
    val frontRoute: FrontRoute,
    val taskConfiguration: TaskConfiguration,
    val emailConfiguration: EmailConfiguration,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseCompanyController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  val noAccessReadingDelay = taskConfiguration.report.noAccessReadingDelay
  val contactAddress = emailConfiguration.contactAddress

  def fetchHosts(companyId: UUID) = SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)).async {
    companyOrchestrator.fetchHosts(companyId).map(x => Ok(Json.toJson(x)))
  }

  def create() = SecuredAction(WithPermission(UserPermission.updateCompany)).async(parse.json) { implicit request =>
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

  def searchRegistered() = SecuredAction(WithRole(UserRole.Admin, UserRole.DGCCRF)).async { request =>
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

  def searchCompanyByWebsite(url: String) = UnsecuredAction.async { _ =>
    companyOrchestrator
      .searchCompanyByWebsite(url)
      .map(results => Ok(Json.toJson(results)))
  }

  def getResponseRate(companyId: UUID) = SecuredAction.async { request =>
    companyOrchestrator
      .getCompanyResponseRate(companyId, request.identity.userRole)
      .map(results => Ok(Json.toJson(results)))
  }

  def companiesToActivate() = SecuredAction(WithRole(UserRole.Admin)).async { _ =>
    companyOrchestrator
      .companiesToActivate()
      .map(result => Ok(Json.toJson(result)))
  }

  def visibleCompanies() = SecuredAction(WithRole(UserRole.Professionnel)).async { implicit request =>
    companiesVisibilityOrchestrator
      .fetchVisibleCompanies(request.identity)
      .map(x => Ok(Json.toJson(x)))
  }

  def getActivationDocument() = SecuredAction(WithPermission(UserPermission.editDocuments)).async(parse.json) {
    implicit request =>
      import CompanyObjects.CompanyList
      request.body
        .validate[CompanyList](Json.reads[CompanyList])
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          results =>
            for {
              companies <- companyRepository.fetchCompanies(results.companyIds)
              activationCodesMap <- accessTokenRepository.prefetchActivationCodes(results.companyIds)
              eventsMap <- eventRepository.fetchEvents(results.companyIds)
              reports <- reportRepository.getPendingReports(results.companyIds)
            } yield {
              val reportsMap = reports.filter(_.companyId.isDefined).groupBy(_.companyId.get)
              val htmlDocuments = companies.flatMap(c =>
                activationCodesMap
                  .get(c.id)
                  .map(
                    getHtmlDocumentForCompany(
                      c,
                      reportsMap.getOrElse(c.id, Nil),
                      eventsMap.getOrElse(c.id, Nil),
                      _
                    )
                  )
              )
              if (!htmlDocuments.isEmpty) {
                pdfService.Ok(htmlDocuments)
              } else {
                NotFound
              }
            }
        )
  }

  private def getHtmlDocumentForCompany(
      company: Company,
      reports: List[Report],
      events: List[Event],
      activationKey: String
  ) = {
    val lastContactLocalDate = events
      .filter(e =>
        e.creationDate.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minus(noAccessReadingDelay))
          && List(ActionEvent.POST_ACCOUNT_ACTIVATION_DOC, ActionEvent.EMAIL_PRO_REMIND_NO_READING).contains(e.action)
      )
      .sortBy(_.creationDate)
      .reverse
      .headOption
      .map(_.creationDate.toLocalDate)

    val report = reports.sortBy(_.creationDate).reverse.headOption
    val reportCreationLocalDate = report.map(_.creationDate.toLocalDate)
    val reportExpirationLocalDate = report.map(_.creationDate.plus(noAccessReadingDelay).toLocalDate)

    lastContactLocalDate
      .map { lastContact =>
        views.html.pdfs.accountActivationReminder(
          company,
          lastContact,
          reportExpirationLocalDate,
          activationKey
        )(frontRoute = frontRoute, contactAddress = contactAddress)
      }
      .getOrElse {
        views.html.pdfs.accountActivation(
          company,
          reportCreationLocalDate,
          reportExpirationLocalDate,
          activationKey
        )(frontRoute = frontRoute, contactAddress = contactAddress)
      }
  }

  def confirmContactByPostOnCompanyList() = SecuredAction(WithRole(UserRole.Admin)).async(parse.json) {
    implicit request =>
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

  def updateCompanyAddress(id: UUID) = SecuredAction(WithPermission(UserPermission.updateCompany)).async(parse.json) {
    implicit request =>
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

  def handleUndeliveredDocument(siret: String) = SecuredAction(WithRole(UserRole.Admin)).async(parse.json) {
    implicit request =>
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
