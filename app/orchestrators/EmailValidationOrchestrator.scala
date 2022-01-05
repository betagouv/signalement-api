package orchestrators

import config.AppConfigLoader
import models.EmailValidation
import models.EmailValidationCreate
import repositories._
import services.Email.ConsumerValidateEmail
import services.MailService
import utils.EmailAddress

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailValidationOrchestrator @Inject() (
    mailService: MailService,
    emailValidationRepository: EmailValidationRepository,
    appConfigLoader: AppConfigLoader
)(implicit
    executionContext: ExecutionContext
) {

  private[this] def findOrCreate(email: EmailAddress): Future[EmailValidation] =
    emailValidationRepository.findByEmail(email).flatMap {
      case None             => emailValidationRepository.create(EmailValidationCreate(email = email))
      case Some(foundEmail) => Future(foundEmail)
    }

  def isEmailValid(email: EmailAddress): Future[Boolean] =
    for {
      emailValidation <- emailValidationRepository.findByEmail(email)
    } yield emailValidation.exists(_.lastValidationDate.isDefined)

  def sendEmailConfirmationIfNeeded(email: EmailAddress): Future[Boolean] =
    if (appConfigLoader.get.mail.skipReportEmailValidation) {
      Future.successful(true)
    } else {
      for {
        emailValidation <- findOrCreate(email)
        res <-
          if (emailValidation.lastValidationDate.isEmpty) {
            mailService.send(ConsumerValidateEmail(emailValidation)).map(_ => false)
          } else Future.successful(true)
      } yield res

    }
}
