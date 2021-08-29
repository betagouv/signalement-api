package orchestrators

import models.EmailValidation
import models.EmailValidationCreate
import play.api.Logger
import play.api.mvc.Request
import repositories._
import services.MailService
import utils.EmailAddress
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailValidationOrchestrator @Inject() (
    mailService: MailService,
    emailValidationRepository: EmailValidationRepository
)(implicit
    executionContext: ExecutionContext
) {

  private[this] val logger = Logger(this.getClass)

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
    for {
      emailValidation <- findOrCreate(email)
    } yield
      if (emailValidation.lastValidationDate.isEmpty) {
        mailService.sendConsumerEmailConfirmation(emailValidation)
        false
      } else true
}
