package controllers

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Singleton}
import models._
import play.api.libs.json._
import play.api.libs.ws._
import play.api.{Configuration, Logger}
import repositories._
import services.PDFService
import utils.Constants.{ActionEvent, EventType}
import utils.silhouette.auth.{AuthEnv, WithPermission, WithRole}
import utils.{EmailAddress, SIRET}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class CompanyController @Inject()(
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

  val reportReminderByPostDelay = java.time.Period.parse(configuration.get[String]("play.reports.reportReminderByPostDelay"))
  val noAccessReadingDelay = java.time.Period.parse(configuration.get[String]("play.reports.noAccessReadingDelay"))
  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")

  def searchRegisteredCompany(q: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      companies <- q match {
        case q if q.matches("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}") => companyRepository.findByShortId(q)
        case q if q.matches("[0-9]{14}") => companyRepository.findBySiret(SIRET(q)).map(_.toList)
        case q => companyRepository.findByName(q)
      }
    } yield Ok(Json.toJson(companies))
  }

  def searchCompany(q: String, postalCode: String) = UnsecuredAction.async { implicit request =>
    logger.debug(s"searchCompany $postalCode $q")
    companyDataRepository.search(q, postalCode).map(results =>
      Ok(Json.toJson(results.map(result => result._1.toSearchResult(result._2.map(_.label)))))
    )
  }

  def searchCompanyBySiret(siret: String) = UnsecuredAction.async { implicit request =>
    logger.debug(s"searchCompanyBySiret $siret")
    companyDataRepository.searchBySiret(siret).map(results =>
      Ok(Json.toJson(results.map{case (company, activity) => company.toSearchResult(activity.map(_.label))}))
    )
  }

  def searchCompanyByWebsite(url: String) = UnsecuredAction.async { implicit request =>
    logger.debug(s"searchCompaniesByHost $url")
    for {
      companiesByUrl <- websiteRepository.searchCompaniesByUrl(url)
      results <- Future.sequence(companiesByUrl.map { case (website, company) =>
        companyDataRepository.searchBySiret(company.siret.toString).map(_.map { case (company, activity) => company.toSearchResult(activity.map(_.label), website.kind) })
      })
    }
      yield
        Ok(Json.toJson(results.flatten))
  }


  def companyDetails(siret: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      company <- companyRepository.findBySiret(SIRET(siret))
    } yield company.map(c => Ok(Json.toJson(c))).getOrElse(NotFound)
  }

  def companiesToActivate() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      accesses <- accessTokenRepository.companiesToActivate()
      eventsMap <- eventRepository.fetchEvents(accesses.map {case (_, c) => c.id})
    } yield Ok(
      Json.toJson(accesses.map {case (t, c) =>
          (c, t, eventsMap.get(c.id).flatMap(
              _.filter(e =>
                e.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
                && e.creationDate.filter(_.isAfter(OffsetDateTime.now.minus(noAccessReadingDelay))).isDefined
              ).headOption
            ).flatMap(_.creationDate))
        }.filter {case (c, t, lastNotice) => lastNotice.filter(_.isAfter(OffsetDateTime.now.minus(reportReminderByPostDelay))).isEmpty}.map {
          case (c, t, lastNotice) =>
            Json.obj(
              "company" -> Json.toJson(c),
              "lastNotice" -> lastNotice,
              "tokenCreation" -> t.creationDate
            )
      })
    )
  }

  def getActivationDocument() = SecuredAction(WithPermission(UserPermission.editDocuments)).async(parse.json) { implicit request =>
    import CompanyObjects.CompanyList
    request.body.validate[CompanyList](Json.reads[CompanyList]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      results => {
        for {
          companies           <- companyRepository.fetchCompanies(results.companyIds)
          activationCodesMap  <- accessTokenRepository.prefetchActivationCodes(results.companyIds)
          eventsMap           <- eventRepository.fetchEvents(results.companyIds)
          reports             <- reportRepository.getPendingReports(results.companyIds)
        } yield {
          val reportsMap = reports.filter(_.companyId.isDefined).groupBy(_.companyId.get)
          val htmlDocuments = companies.flatMap(c =>
            activationCodesMap.get(c.id).map(getHtmlDocumentForCompany(
              c,
              reportsMap.get(c.id).getOrElse(Nil),
              eventsMap.get(c.id).getOrElse(Nil),
              _
            ))
          )
          if (!htmlDocuments.isEmpty) {
            pdfService.Ok(htmlDocuments)
          } else {
            NotFound
          }
        }
      }
    )
  }

  def getHtmlDocumentForCompany(company: Company, reports: List[Report], events: List[Event], activationKey: String) = {
    val lastContact = events.filter(e =>
                              e.creationDate.filter(_.isAfter(OffsetDateTime.now.minus(noAccessReadingDelay))).isDefined
                              && List(ActionEvent.POST_ACCOUNT_ACTIVATION_DOC, ActionEvent.EMAIL_PRO_REMIND_NO_READING).contains(e.action))
                        .sortBy(_.creationDate).reverse.headOption
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
        activationKey
      )
  }

  def confirmContactByPostOnCompanyList() = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    import CompanyObjects.CompanyList

    request.body.validate[CompanyList](Json.reads[CompanyList]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      companyList => {
        Future.sequence(companyList.companyIds.map(companyId => {
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
        })).map(_ => Ok)
      }
    )
  }

  def updateCompanyAddress(siret: String) = SecuredAction(WithPermission(UserPermission.updateCompany)).async(parse.json) { implicit request =>
    request.body.validate[CompanyAddress].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      companyAddress => for {
        company <- companyRepository.findBySiret(SIRET(siret))
        updatedCompany <- company.map(c =>
          companyRepository.update(c.copy(address = companyAddress.address, postalCode = Some(companyAddress.postalCode))).map(Some(_))
        ).getOrElse(Future(None))
      } yield updatedCompany.map(c => Ok(Json.toJson(c))).getOrElse(NotFound)
    )
  }
}

object CompanyObjects {
  case class CompanyList(companyIds: List[UUID])
}