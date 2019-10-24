package controllers

import java.io.{ByteArrayInputStream, File}

import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import com.itextpdf.html2pdf.{ConverterProperties, HtmlConverter}
import com.itextpdf.kernel.pdf.{PdfDocument, PdfWriter}
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models.{PasswordChange, User, UserPermission, UserRoles}
import play.api._
import play.api.libs.json.JsError
import repositories.{EventFilter, EventRepository, ReportFilter, ReportRepository, UserRepository}
import utils.Constants.{ActionEvent, ReportStatus}
import utils.silhouette.auth.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountController @Inject()(
                                   val silhouette: Silhouette[AuthEnv],
                                   userRepository: UserRepository,
                                   reportRepository: ReportRepository,
                                   eventRepository: EventRepository,
                                   credentialsProvider: CredentialsProvider,
                                   configuration: Configuration
                              )(implicit ec: ExecutionContext)
 extends BaseController {

  val logger: Logger = Logger(this.getClass())

  def changePassword = SecuredAction.async(parse.json) { implicit request =>

    logger.debug("changePassword")

    request.body.validate[PasswordChange].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      passwordChange => {
        for {
          identLogin <- credentialsProvider.authenticate(Credentials(request.identity.login, passwordChange.oldPassword))
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

  def activateAccount = SecuredAction(WithPermission(UserPermission.activateAccount)).async(parse.json) { implicit request =>

    logger.debug("activateAccount")

    request.body.validate[User].fold(
      errors => {
        Future.successful(BadRequest(JsError.toJson(errors)))
      },
      user => {
        for {
          _ <- userRepository.update(user)
          _ <- userRepository.updateAccountActivation(request.identity.id, None, UserRoles.Pro)
          _ <- userRepository.updatePassword(request.identity.id, user.password)
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

  def getActivationDocument(siret: String) = SecuredAction(WithPermission(UserPermission.editDocuments)).async { implicit request =>

    for {
      user <- userRepository.findByLogin(siret)
      paginatedReports <- reportRepository.getReports(0, 1, ReportFilter(siret = Some(siret), statusList = Seq(ReportStatus.A_TRAITER.defaulValue)))
      report <- paginatedReports.entities match {
        case report :: otherReports => Future(Some(report))
        case Nil => Future(None)
      }
      reminderExists <- report match {
        case Some(report) => eventRepository.getEvents(report.id.get, EventFilter(action = Some(ActionEvent.RELANCE))).map(!_.isEmpty)
        case _ => Future(List())
      }
    } yield {
      (report, user) match {
        case (Some(report), Some(user)) if user.activationKey.isDefined =>

          val tmpFileName = s"${configuration.get[String]("play.tmpDirectory")}/activation_${siret}.pdf";
          val pdf = new PdfDocument(new PdfWriter(tmpFileName))

          val converterProperties = new ConverterProperties
          val dfp = new DefaultFontProvider(true, true, true)
          converterProperties.setFontProvider(dfp)
          converterProperties.setBaseUri(configuration.get[String]("play.application.url"))

          val pdfString = reminderExists match {
            case false => views.html.pdfs.accountActivation(
              report.companyAddress,
              report.creationDate.map(_.toLocalDate).get,
              user.activationKey.get
            )
            case true => views.html.pdfs.accountActivationReminder(
              report.companyAddress,
              user.activationKey.get
            )
          }

          HtmlConverter.convertToPdf(new ByteArrayInputStream(pdfString.body.getBytes()), pdf, converterProperties)

          Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)

        case (Some(report), Some(user)) => NotFound("Il n'y a pas de code d'activation associé à ce Siret")
        case (Some(report), None) => NotFound("Il n'y a pas d'utilisateur associé à ce Siret")
        case (None, _) => NotFound("Il n'y a pas de signalement à traiter associé à ce Siret")
      }
    }
  }

}