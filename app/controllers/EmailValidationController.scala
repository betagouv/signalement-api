package controllers

import com.mohiva.play.silhouette.api.Silhouette
import orchestrators.EmailValidationOrchestrator
import play.api._
import play.api.libs.json.JsError
import play.api.libs.json.Json
import repositories._
import utils.EmailAddress
import utils.silhouette.auth.AuthEnv

import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class EmailValidationController @Inject() (
    val silhouette: Silhouette[AuthEnv],
    emailValidationRepository: EmailValidationRepository,
    emailValidationOrchestrator: EmailValidationOrchestrator
)(implicit
    ec: ExecutionContext
) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  case class EmailBody(email: EmailAddress)

  def checkEmail() = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[EmailBody](Json.reads[EmailBody])
      .fold(
        errors => {
          logger.error(s"$errors")
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        body =>
          emailValidationOrchestrator
            .sendEmailConfirmationIfNeeded(body.email)
            .map(valid => Ok(Json.obj("valid" -> valid)))
      )
  }

  case class EmailValidationBody(email: EmailAddress, confirmationCode: String)

  def validEmail() = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[EmailValidationBody](Json.reads[EmailValidationBody])
      .fold(
        errors => {
          logger.error(s"$errors")
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        body =>
          emailValidationRepository.findByEmail(body.email).flatMap { emailValidationOpt =>
            emailValidationOpt
              .map { emailValidation =>
                if (emailValidation.confirmationCode == body.confirmationCode)
                  emailValidationRepository.validate(body.email).map(emailValidation => Ok(Json.obj("valid" -> true)))
                // TODO Could be nice to handle some day
                // else if (emailValidation.attempts > 10)
                //   Future(Ok(Json.obj("valid" -> false, "reason" -> "TOO_MANY_ATTEMPTS")))
                else
                  emailValidationRepository
                    .update(
                      emailValidation.copy(
                        attempts = emailValidation.attempts + 1,
                        lastAttempt = Some(OffsetDateTime.now)
                      )
                    )
                    .map(x => Ok(Json.obj("valid" -> false, "reason" -> "INVALID_CODE")))
              }
              .getOrElse(Future(NotFound))
          }
      )
  }
}
