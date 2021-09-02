package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.Event.stringToDetailsJsValue
import models.PaginatedResult.paginatedResultWrites
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Configuration
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws._
import repositories._
import services.PDFService
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.EmailAddress
import utils.SIREN
import utils.SIRET
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission
import utils.silhouette.auth.WithRole

import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyController @Inject() (
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    val userRepository: UserRepository,
    val companyRepository: CompanyRepository,
    val companyDataRepository: CompanyDataRepository,
    val websiteRepository: WebsiteRepository,
    val accessTokenRepository: AccessTokenRepository,
    val eventRepository: EventRepository,
    val reportRepository: ReportRepository,
    val pdfService: PDFService,
    val silhouette: Silhouette[AuthEnv],
    val configuration: Configuration,
    ws: WSClient
)(implicit ec: ExecutionContext)
    extends BaseCompanyController {

  val logger: Logger = Logger(this.getClass)

  val reportReminderByPostDelay =
    java.time.Period.parse(configuration.get[String]("play.reports.reportReminderByPostDelay"))
  val noAccessReadingDelay = java.time.Period.parse(configuration.get[String]("play.reports.noAccessReadingDelay"))
  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")

  def create() = SecuredAction(WithPermission(UserPermission.updateCompany)).async(parse.json) { implicit request =>
    request.body
      .validate[CompanyCreation]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        companyCreation =>
          companyRepository
            .getOrCreate(companyCreation.siret, companyCreation.toCompany())
            .map(company => Ok(Json.toJson(company)))
      )
  }

  /** @deprecated replaced by CompanyController.searchRegistered */
  def searchRegisteredCompany(q: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      companies <- q match {
                     case q if q.matches("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}") => companyRepository.findByShortId(q)
                     case q if q.matches("[0-9]{9}")                      => companyRepository.findBySiret(SIRET(q)).map(_.toList)
                     case q if q.matches("[0-9]{14}")                     => companyRepository.findBySiret(SIRET(q)).map(_.toList)
                     case q                                               => companyRepository.findByName(q)
                   }
    } yield Ok(Json.toJson(companies))
  }

  def searchRegistered(
      departments: Option[Seq[String]],
      identity: Option[String],
      offset: Option[Long],
      limit: Option[Int]
  ) = SecuredAction(WithRole(UserRoles.Admin, UserRoles.DGCCRF)).async { implicit request =>
    def removeSpacesIfSirenSirets(identity: String): String =
      if (identity.replaceAll("\\s", "").matches("^([0-9]{9}|[0-9]{14})$"))
        identity.replaceAll("\\s", "")
      else
        identity

    companyRepository
      .searchWithReportsCount(
        departments = departments.getOrElse(Seq()),
        identity = identity.map(removeSpacesIfSirenSirets),
        offset = offset,
        limit = limit
      )
      .map(res => Ok(Json.toJson(res)(paginatedResultWrites[CompanyWithNbReports])))
  }

  def searchCompany(q: String, postalCode: String) = UnsecuredAction.async { implicit request =>
    logger.debug(s"searchCompany $postalCode $q")
    companyDataRepository
      .search(q, postalCode)
      .map(results => Ok(Json.toJson(results.map(result => result._1.toSearchResult(result._2.map(_.label))))))
  }

  def searchCompanyByIdentity(identity: String) = UnsecuredAction.async { implicit request =>
    logger.debug(s"searchCompanyByIdentity $identity")

    (identity.replaceAll("\\s", "") match {
      case q if q.matches(SIRET.pattern) => companyDataRepository.searchBySiretWithHeadOffice(SIRET(q))
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
      Ok(Json.toJson(companiesWithActivity.map { case (company, activity) =>
        company.toSearchResult(activity.map(_.label))
      }))
    )

  }

  def searchCompanyByWebsite(url: String) = UnsecuredAction.async { implicit request =>
    logger.debug(s"searchCompaniesByHost $url")
    for {
      companiesByUrl <-
        websiteRepository.searchCompaniesByUrl(url, Some(Seq(WebsiteKind.DEFAULT, WebsiteKind.MARKETPLACE)))
      results <- Future.sequence(companiesByUrl.map { case (website, company) =>
                   companyDataRepository
                     .searchBySiret(company.siret)
                     .map(_.map { case (company, activity) =>
                       company.toSearchResult(activity.map(_.label), website.kind == WebsiteKind.MARKETPLACE)
                     })
                 })
    } yield Ok(Json.toJson(results.flatten))
  }

  def companyDetails(siret: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      company <- companyRepository.findBySiret(SIRET(siret))
    } yield company.map(c => Ok(Json.toJson(c))).getOrElse(NotFound)
  }

  def companiesToActivate() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      accesses <- accessTokenRepository.companiesToActivate()
      eventsMap <- eventRepository.fetchEvents(accesses.map { case (_, c) => c.id })
    } yield Ok(
      Json.toJson(
        accesses
          .map { case (t, c) =>
            (
              c,
              t,
              eventsMap
                .get(c.id)
                .map(_.count(e => e.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC))
                .getOrElse(0),
              eventsMap
                .get(c.id)
                .flatMap(_.find(e => e.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC))
                .flatMap(_.creationDate),
              eventsMap
                .get(c.id)
                .flatMap(_.find(e => e.action == ActionEvent.ACTIVATION_DOC_REQUIRED))
                .flatMap(_.creationDate)
            )
          }
          .filter { case (c, t, noticeCount, lastNotice, lastRequirement) =>
            !lastNotice.exists(
              _.isAfter(
                lastRequirement.getOrElse(
                  OffsetDateTime.now.minus(reportReminderByPostDelay.multipliedBy(Math.min(noticeCount, 3)))
                )
              )
            )
          }
          .map { case (c, t, _, lastNotice, _) =>
            Json.obj(
              "company" -> Json.toJson(c),
              "lastNotice" -> lastNotice,
              "tokenCreation" -> t.creationDate
            )
          }
      )
    )
  }

  def visibleCompanies() = SecuredAction(WithRole(UserRoles.Pro)).async { implicit request =>
    companiesVisibilityOrchestrator
      .fetchVisibleCompanies(request.identity)
      .map(companies =>
        companies.map(c =>
          VisibleCompany(
            c.siret,
            c.codePostalEtablissement,
            c.etatAdministratifEtablissement.contains("F")
          )
        )
      )
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

  def getHtmlDocumentForCompany(company: Company, reports: List[Report], events: List[Event], activationKey: String) = {
    val lastContact = events
      .filter(e =>
        e.creationDate.exists(_.isAfter(OffsetDateTime.now.minus(noAccessReadingDelay)))
          && List(ActionEvent.POST_ACCOUNT_ACTIVATION_DOC, ActionEvent.EMAIL_PRO_REMIND_NO_READING).contains(e.action)
      )
      .sortBy(_.creationDate)
      .reverse
      .headOption
    val report = reports.sortBy(_.creationDate).reverse.headOption
    if (lastContact.isDefined)
      views.html.pdfs.accountActivationReminder(
        company,
        lastContact.flatMap(_.creationDate).getOrElse(company.creationDate).toLocalDate,
        report.map(_.creationDate).getOrElse(company.creationDate).toLocalDate.plus(noAccessReadingDelay),
        activationKey
      )
    else
      views.html.pdfs.accountActivation(
        company,
        report.map(_.creationDate).getOrElse(company.creationDate).toLocalDate,
        report.map(_.creationDate).getOrElse(company.creationDate).toLocalDate.plus(noAccessReadingDelay),
        activationKey
      )
  }

  def confirmContactByPostOnCompanyList() = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) {
    implicit request =>
      import CompanyObjects.CompanyList

      request.body
        .validate[CompanyList](Json.reads[CompanyList])
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          companyList =>
            Future
              .sequence(companyList.companyIds.map { companyId =>
                eventRepository.createEvent(
                  Event(
                    Some(UUID.randomUUID()),
                    None,
                    Some(companyId),
                    Some(request.identity.id),
                    Some(OffsetDateTime.now()),
                    EventType.PRO,
                    ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
                  )
                )
              })
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
            for {
              company <- companyRepository.fetchCompany(id)
              updatedCompany <-
                company
                  .map(c => companyRepository.update(c.copy(address = companyAddressUpdate.address)).map(Some(_)))
                  .getOrElse(Future(None))
              _ <- updatedCompany
                     .filter(c => !company.map(_.address).contains(c.address))
                     .map(c =>
                       eventRepository.createEvent(
                         Event(
                           Some(UUID.randomUUID()),
                           None,
                           Some(c.id),
                           Some(request.identity.id),
                           Some(OffsetDateTime.now()),
                           EventType.PRO,
                           ActionEvent.COMPANY_ADDRESS_CHANGE,
                           stringToDetailsJsValue(s"Addresse précédente : ${company.map(_.address).getOrElse("")}")
                         )
                       )
                     )
                     .getOrElse(Future(None))
              _ <- updatedCompany
                     .filter(_ => companyAddressUpdate.activationDocumentRequired)
                     .map(c =>
                       eventRepository.createEvent(
                         Event(
                           Some(UUID.randomUUID()),
                           None,
                           Some(c.id),
                           Some(request.identity.id),
                           Some(OffsetDateTime.now()),
                           EventType.PRO,
                           ActionEvent.ACTIVATION_DOC_REQUIRED
                         )
                       )
                     )
                     .getOrElse(Future(None))
            } yield updatedCompany.map(c => Ok(Json.toJson(c))).getOrElse(NotFound)
        )
  }

  def handleUndeliveredDocument(siret: String) = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) {
    implicit request =>
      request.body
        .validate[UndeliveredDocument]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          undeliveredDocument =>
            for {
              company <- companyRepository.findBySiret(SIRET(siret))
              event <- company
                         .map(c =>
                           eventRepository
                             .createEvent(
                               Event(
                                 Some(UUID.randomUUID()),
                                 None,
                                 Some(c.id),
                                 Some(request.identity.id),
                                 Some(OffsetDateTime.now()),
                                 EventType.ADMIN,
                                 ActionEvent.ACTIVATION_DOC_RETURNED,
                                 stringToDetailsJsValue(s"Date de retour : ${undeliveredDocument.returnedDate
                                   .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
                               )
                             )
                             .map(Some(_))
                         )
                         .getOrElse(Future(None))
            } yield event.map(e => Ok(Json.toJson(e))).getOrElse(NotFound)
        )
  }
}

object CompanyObjects {
  case class CompanyList(companyIds: List[UUID])
}
