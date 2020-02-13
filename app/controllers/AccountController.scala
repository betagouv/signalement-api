package controllers

import java.io.{ByteArrayInputStream, File}
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import com.itextpdf.html2pdf.{ConverterProperties, HtmlConverter}
import com.itextpdf.kernel.pdf.{PdfDocument, PdfWriter}
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models._
import orchestrators._
import play.api._
import play.api.libs.json.{JsError, JsPath, Json}
import repositories._
import utils.Constants.ReportStatus.A_TRAITER
import utils.Constants.{ActionEvent, ReportStatus}
import utils.silhouette.auth.{AuthEnv, WithPermission}
import utils.{EmailAddress, SIRET}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountController @Inject()(
                                   val silhouette: Silhouette[AuthEnv],
                                   userRepository: UserRepository,
                                   companyRepository: CompanyRepository,
                                   accessTokenRepository: AccessTokenRepository,
                                   accessesOrchestrator: AccessesOrchestrator,
                                   reportRepository: ReportRepository,
                                   eventRepository: EventRepository,
                                   credentialsProvider: CredentialsProvider,
                                   configuration: Configuration
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass())
  val reportReminderByPostDelay = java.time.Period.parse(configuration.get[String]("play.reports.reportReminderByPostDelay"))

  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")

  def changePassword = SecuredAction.async(parse.json) { implicit request =>
    request.body.validate[PasswordChange].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      passwordChange => {
        for {
          identLogin <- credentialsProvider.authenticate(Credentials(request.identity.email.value, passwordChange.oldPassword))
          _ <- userRepository.updatePassword(request.identity.id, passwordChange.newPassword)
        } yield {
          NoContent
        }
      }.recover {
        case e => {
          Unauthorized
        }
      }
    )
  }

  def activateAccount = UnsecuredAction.async(parse.json) { implicit request =>
    request.body.validate[ActivationRequest].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      {
        case ActivationRequest(draftUser, token, companySiret) =>
          accessesOrchestrator
            .handleActivationRequest(draftUser, token, companySiret)
            .map {
              case accessesOrchestrator.ActivationOutcome.NotFound      => NotFound
              case accessesOrchestrator.ActivationOutcome.EmailConflict => Conflict  // HTTP 409
              case accessesOrchestrator.ActivationOutcome.Success       => NoContent
            }
      }
    )
  }

  def getActivationDocument(siret: String) = SecuredAction(WithPermission(UserPermission.editDocuments)).async { implicit request =>
    for {
      company <- companyRepository.findBySiret(SIRET(siret))
      token <- company.map(accessTokenRepository.fetchActivationCode(_)).getOrElse(Future(None))
      paginatedReports <- reportRepository.getReports(0, 1, ReportFilter(siret = Some(siret), statusList = Seq(ReportStatus.A_TRAITER)))
      report <- paginatedReports.entities match {
        case report :: otherReports => Future(Some(report))
        case Nil => Future(None)
      }
      events <- report match {
        case Some(report) => eventRepository.getEvents(report.id, EventFilter(action = None))
        case _ => Future(List())
      }
    } yield {
      (report, token) match {
        case (Some(report), Some(activationKey)) =>

          val tmpFileName = s"${configuration.get[String]("play.tmpDirectory")}/activation_${siret}.pdf";
          val pdf = new PdfDocument(new PdfWriter(tmpFileName))

          val converterProperties = new ConverterProperties
          val dfp = new DefaultFontProvider(true, true, true)
          converterProperties.setFontProvider(dfp)
          converterProperties.setBaseUri(configuration.get[String]("play.application.url"))
        
          HtmlConverter.convertToPdf(new ByteArrayInputStream(getHtmlDocumentForReport(report, events, activationKey).body.getBytes()), pdf, converterProperties)
        
          Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
        case (Some(report), None) => NotFound("Il n'y a pas de code d'activation associé à ce Siret")
        case (None, _) => NotFound("Il n'y a pas de signalement à traiter associé à ce Siret")
      }
    }
  }

  def getHtmlDocumentForReport(report: Report, events: List[Event], activationKey: String) = {
    val creationDate = events
      .filter(_.action == ActionEvent.CONTACT_COURRIER)
      .headOption
      .flatMap(_.creationDate)
      .getOrElse(report.creationDate)
      .toLocalDate
    val remindEvent = events.find(_.action == ActionEvent.RELANCE)
    remindEvent.map(remindEvent =>
        views.html.pdfs.accountActivationReminder(
          report.companyAddress,
          creationDate,
          remindEvent.creationDate.map(_.toLocalDate).get.plus(reportReminderByPostDelay),
          activationKey
        )
    ).getOrElse(
      views.html.pdfs.accountActivation(
        report.companyAddress,
        report.creationDate.toLocalDate,
        activationKey
      )
    )
  }

  def getActivationDocumentForReportList() = SecuredAction(WithPermission(UserPermission.editDocuments)).async(parse.json) { implicit request =>

    import AccountObjects.ReportList

    request.body.validate[ReportList](Json.reads[ReportList]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      result => {
        for {
          reports <- reportRepository.getReportsByIds(result.reportIds).map(_.filter(_.status == A_TRAITER))
          reportEventsMap <- eventRepository.prefetchReportsEvents(reports)
          reportActivationCodesMap <- accessTokenRepository.prefetchActivationCodes(reports.flatMap(_.companyId))
        } yield {

          val htmlDocuments = reports
            .map(report => (report, reportEventsMap.get(report.id), reportActivationCodesMap.get(report.companyId.get)))
            .filter(_._3.isDefined)
            .map(tuple => getHtmlDocumentForReport(tuple._1, tuple._2.getOrElse(List.empty), tuple._3.get))

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
  def sendDGCCRFInvitation = SecuredAction(WithPermission(UserPermission.inviteDGCCRF)).async(parse.json) { implicit request =>
    request.body.validate[EmailAddress]((JsPath \ "email").read[EmailAddress]).fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      email => accessesOrchestrator.sendDGCCRFInvitation(email).map(_ => Ok)
    )
  }
  def fetchTokenInfo(token: String) = UnsecuredAction.async { implicit request =>
    for {
      accessToken <- accessTokenRepository.findToken(token)
    } yield accessToken.map(t =>
      Ok(Json.toJson(TokenInfo(t.token, t.kind, None, t.emailedTo)))
    ).getOrElse(NotFound)
  }
}

object AccountObjects {
  case class ReportList(reportIds: List[UUID])
}