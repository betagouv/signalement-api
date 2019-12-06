package controllers

import java.io.{ByteArrayInputStream, File}
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
import play.api.libs.json.JsError
import repositories._
import utils.Constants.{ActionEvent, ReportStatus}
import utils.silhouette.auth.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountController @Inject()(
                                   val silhouette: Silhouette[AuthEnv],
                                   userRepository: UserRepository,
                                   companyRepository: CompanyRepository,
                                   companyAccessRepository: CompanyAccessRepository,
                                   companyAccessOrchestrator: CompanyAccessOrchestrator,
                                   reportRepository: ReportRepository,
                                   eventRepository: EventRepository,
                                   credentialsProvider: CredentialsProvider,
                                   configuration: Configuration
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass())
  val reportExpirationDelay = java.time.Period.parse(configuration.get[String]("play.reports.expirationDelay"))

  def changePassword = SecuredAction.async(parse.json) { implicit request =>

    logger.debug("changePassword")

    request.body.validate[PasswordChange].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      passwordChange => {
        for {
          identLogin <- credentialsProvider.authenticate(Credentials(request.identity.email.map(_.value).get, passwordChange.oldPassword))
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

    logger.debug("activateAccount")

    request.body.validate[ActivationRequest].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      {case ActivationRequest(draftUser, tokenInfo) =>
            companyAccessOrchestrator
              .handleActivationRequest(draftUser, tokenInfo)
              .map(_ => NoContent)
      }
    )
  }

  def getActivationDocument(siret: String) = SecuredAction(WithPermission(UserPermission.editDocuments)).async { implicit request =>

    for {
      company <- companyRepository.findBySiret(siret)
      token <- company.map(companyAccessRepository.fetchActivationCode(_)).getOrElse(Future(None))
      paginatedReports <- reportRepository.getReports(0, 1, ReportFilter(siret = Some(siret), statusList = Seq(ReportStatus.A_TRAITER.defaultValue)))
      report <- paginatedReports.entities match {
        case report :: otherReports => Future(Some(report))
        case Nil => Future(None)
      }
      events <- report match {
        case Some(report) => eventRepository.getEvents(report.id.get, EventFilter(action = None))
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
          val creationDate = events
                            .filter(_.action == ActionEvent.CONTACT_COURRIER)
                            .headOption
                            .flatMap(_.creationDate)
                            .getOrElse(report.creationDate.get)
                            .toLocalDate
          val pdfString = if (events.exists(_.action == ActionEvent.RELANCE)) {
              views.html.pdfs.accountActivationReminder(
                report.companyAddress,
                creationDate,
                creationDate.plus(reportExpirationDelay),
                activationKey
              )
            } else {
              views.html.pdfs.accountActivation(
                report.companyAddress,
                report.creationDate.map(_.toLocalDate).get,
                activationKey
              )
            }

          HtmlConverter.convertToPdf(new ByteArrayInputStream(pdfString.body.getBytes()), pdf, converterProperties)

          Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
        case (Some(report), None) => NotFound("Il n'y a pas de code d'activation associé à ce Siret")
        case (None, _) => NotFound("Il n'y a pas de signalement à traiter associé à ce Siret")
      }
    }
  }

}