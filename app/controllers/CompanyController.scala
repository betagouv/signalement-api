package controllers

import java.net.URI

import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}
import java.util.UUID
import repositories._
import models._
import orchestrators.AccessesOrchestrator
import play.api.Configuration
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import com.mohiva.play.silhouette.api.Silhouette
import services.PDFService
import utils.silhouette.auth.{AuthEnv, WithRole, WithPermission}
import utils.Constants.{ActionEvent, EventType}
import utils.{EmailAddress, SIRET}


@Singleton
class CompanyController @Inject()(
                                val userRepository: UserRepository,
                                val companyRepository: CompanyRepository,
                                val accessTokenRepository: AccessTokenRepository,
                                val eventRepository: EventRepository,
                                val reportRepository: ReportRepository,
                                val pdfService: PDFService,
                                val silhouette: Silhouette[AuthEnv],
                                val configuration: Configuration
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {
  val reportReminderByPostDelay = java.time.Period.parse(configuration.get[String]("play.reports.reportReminderByPostDelay"))
  val noAccessReadingDelay = java.time.Period.parse(configuration.get[String]("play.reports.noAccessReadingDelay"))
  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")

  def findCompany(q: String) = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      companies <- q match {
        case q if q.matches("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}") => companyRepository.findByShortId(q)
        case q if q.matches("[0-9]{14}") => companyRepository.findBySiret(SIRET(q)).map(_.toList)
        case q => companyRepository.findByName(q)
      }
    } yield Ok(Json.toJson(companies))
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
          (c, t, eventsMap.get(c.id).flatMap(_.filter(_.action == ActionEvent.CONTACT_COURRIER).headOption).flatMap(_.creationDate))
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
    val lastContact = events.filter(e => List(ActionEvent.CONTACT_COURRIER, ActionEvent.RELANCE).contains(e.action))
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
              ActionEvent.CONTACT_COURRIER,
              Json.obj()
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
