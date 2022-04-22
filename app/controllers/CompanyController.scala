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
import repositories.accesstoken.AccessTokenRepository
import repositories.company.CompanyRepository
import repositories.event.EventRepository
import repositories.report.ReportRepository
import repositories.user.UserRepository
import services.PDFService
import utils.Constants.ActionEvent
import utils.FrontRoute
import utils.QueryStringMapper
import utils.SIRET
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission
import utils.silhouette.auth.WithRole

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyController @Inject() (
    val companyOrchestrator: CompanyOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    val userRepository: UserRepository,
    val companyRepository: CompanyRepository,
    val accessTokenRepository: AccessTokenRepository,
    val eventRepository: EventRepository,
    val reportRepository: ReportRepository,
    val pdfService: PDFService,
    val silhouette: Silhouette[AuthEnv],
    val companyVisibilityOrch: CompaniesVisibilityOrchestrator,
    val frontRoute: FrontRoute,
    val taskConfiguration: TaskConfiguration,
    val emailConfiguration: EmailConfiguration
)(implicit val ec: ExecutionContext)
    extends BaseCompanyController {

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

  def searchRegistered() = SecuredAction.async { request =>
    val maybeCompanyId: Option[UUID] = new QueryStringMapper(request.queryString).UUID("identity")
    PaginatedSearch
      .fromQueryString(request.queryString)
      .fold(
        error => {
          logger.error("Cannot parse querystring" + request.queryString, error)
          Future.successful(BadRequest)
        },
        paginationFilters =>
          companyOrchestrator
            .searchRegistered(maybeCompanyId, paginationFilters, request.identity)
            .map(res => Ok(Json.toJson(res)(paginatedResultWrites[CompanyWithNbReports])))
      )
  }

  def searchCompany(q: String, postalCode: String) = UnsecuredAction.async { _ =>
    logger.debug(s"searchCompany $postalCode $q")
    companyOrchestrator
      .searchCompany(q, postalCode)
      .map(results => Ok(Json.toJson(results)))
  }

  def searchCompanyByIdentity(identity: String) = UnsecuredAction.async { _ =>
    logger.debug(s"searchCompanyByIdentity $identity")
    companyOrchestrator
      .searchCompanyByIdentity(identity)
      .map(res => Ok(Json.toJson(res)))
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
    val lastContact = events
      .filter(e =>
        e.creationDate.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minus(noAccessReadingDelay))
          && List(ActionEvent.POST_ACCOUNT_ACTIVATION_DOC, ActionEvent.EMAIL_PRO_REMIND_NO_READING).contains(e.action)
      )
      .sortBy(_.creationDate)
      .reverse
      .headOption
    val report = reports.sortBy(_.creationDate).reverse.headOption
    if (lastContact.isDefined)
      views.html.pdfs.accountActivationReminder(
        company,
        lastContact.map(_.creationDate).getOrElse(company.creationDate).toLocalDate,
        report.map(_.creationDate).getOrElse(company.creationDate).toLocalDate.plus(noAccessReadingDelay),
        activationKey
      )(frontRoute = frontRoute, contactAddress = contactAddress)
    else
      views.html.pdfs.accountActivation(
        company,
        report.map(_.creationDate).getOrElse(company.creationDate).toLocalDate,
        report.map(_.creationDate).getOrElse(company.creationDate).toLocalDate.plus(noAccessReadingDelay),
        activationKey
      )(frontRoute = frontRoute, contactAddress = contactAddress)
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
