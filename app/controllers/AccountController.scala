package controllers

import java.net.URI
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import javax.inject.{Inject, Singleton}
import models._
import orchestrators._
import play.api._
import play.api.libs.json.{JsError, JsPath, Json}
import repositories._
import utils.Constants.{ActionEvent, ReportStatus}
import utils.silhouette.auth.{AuthEnv, WithPermission}
import utils.EmailAddress

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
