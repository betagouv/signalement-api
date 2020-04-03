package controllers


import java.io.{ByteArrayInputStream, File}
import java.net.URI
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import com.itextpdf.html2pdf.{ConverterProperties, HtmlConverter}
import com.itextpdf.kernel.pdf.{PdfDocument, PdfWriter}

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
                                val silhouette: Silhouette[AuthEnv],
                                val configuration: Configuration
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {
  val reportReminderByPostDelay = java.time.Period.parse(configuration.get[String]("play.reports.reportReminderByPostDelay"))
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
      companies <- accessTokenRepository.companiesToActivate()
      eventsMap <- eventRepository.fetchEvents(companies.map(_.id))
    } yield Ok(
      Json.toJson(companies.map(c =>
        Json.obj(
          "company" -> Json.toJson(c),
          "lastNotice"  -> eventsMap.get(c.id).flatMap(_.filter(_.action == ActionEvent.CONTACT_COURRIER).headOption).flatMap(_.creationDate)
        )
      ))
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
            val tmpFileName = s"${configuration.get[String]("play.tmpDirectory")}/courriers_${OffsetDateTime.now.toString}.pdf";
            val pdf = new PdfDocument(new PdfWriter(tmpFileName))

            val converterProperties = new ConverterProperties
            val dfp = new DefaultFontProvider(true, true, true)
            converterProperties.setFontProvider(dfp)
            converterProperties.setBaseUri(configuration.get[String]("play.application.url"))

            HtmlConverter.convertToPdf(new ByteArrayInputStream(htmlDocuments.map(_.body).mkString.getBytes()), pdf, converterProperties)

            Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
          } else {
            NotFound
          }
        }
      }
    )
  }

  def getHtmlDocumentForCompany(company: Company, reports: List[Report], events: List[Event], activationKey: String) = {
    val creationDate = events
      .filter(_.action == ActionEvent.CONTACT_COURRIER)
      .headOption
      .flatMap(_.creationDate)
      .getOrElse(company.creationDate)
      .toLocalDate
    val remindEvent = events.find(_.action == ActionEvent.RELANCE)
    val report = reports.sortBy(_.creationDate).reverse.headOption
    remindEvent.map(e =>
        views.html.pdfs.accountActivationReminder(
          company,
          creationDate,
          e.creationDate.map(_.toLocalDate).get.plus(reportReminderByPostDelay),
          activationKey
        )
    ).getOrElse(
      views.html.pdfs.accountActivation(
        company,
        report.map(_.creationDate).getOrElse(company.creationDate).toLocalDate,
        activationKey
      )
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

  def updateCompany(uuid: String) = SecuredAction(WithPermission(UserPermission.updateCompany)).async(parse.json) { implicit request =>
    request.body.validate[Company].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      company => companyRepository.update(company).map(_ => Ok)
    )
  }
}

object CompanyObjects {
  case class CompanyList(companyIds: List[UUID])
}
