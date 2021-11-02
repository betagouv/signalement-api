package orchestrators

import models.EmailValidation
import models.EmailValidationCreate
import play.api.mvc.Request
import play.api.Configuration
import repositories._
import services.MailService
import utils.EmailAddress

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailValidationOrchestrator @Inject() (
    mailService: MailService,
    emailValidationRepository: EmailValidationRepository,
    configuration: Configuration
)(implicit
    executionContext: ExecutionContext
) {

  val skipEmailValidation = configuration.get[Boolean]("play.mail.skipReportEmailValidation")

  private[this] def findOrCreate(email: EmailAddress): Future[EmailValidation] =
    emailValidationRepository.findByEmail(email).flatMap {
      case None             => emailValidationRepository.create(EmailValidationCreate(email = email))
      case Some(foundEmail) => Future(foundEmail)
    }

  def isEmailValid(email: EmailAddress): Future[Boolean] =
    for {
      emailValidation <- emailValidationRepository.findByEmail(email)
    } yield emailValidation.exists(_.lastValidationDate.isDefined)

  def sendEmailConfirmationIfNeeded(email: EmailAddress)(implicit request: Request[Any]): Future[Boolean] =
    if (skipEmailValidation) {
      Future.successful(true)
    } else {
      for {
        emailValidation <- findOrCreate(email)
      } yield
        if (emailValidation.lastValidationDate.isEmpty) {
          mailService.Consumer.sendEmailConfirmation(emailValidation)
          false
        } else true
    }
}
