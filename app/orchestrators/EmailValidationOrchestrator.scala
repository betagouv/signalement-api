package orchestrators

import cats.implicits.catsSyntaxOption
import config.EmailConfiguration
import controllers.error.AppError
import eu.timepit.refined.api.RefType
import models.EmailApi.EmailString
import models.EmailValidation.EmailValidationThreshold
import models._
import models.consumerconsent.ConsumerConsent
import models.email.EmailValidationResult
import models.email.ValidateEmailCode
import play.api.Logger
import play.api.i18n.MessagesApi
import repositories.consumerconsent.ConsumerConsentRepositoryInterface
import repositories.emailvalidation.EmailValidationRepositoryInterface
import services.emails.EmailDefinitionsConsumer.ConsumerValidateEmail
import services.emails.MailServiceInterface
import utils.EmailAddress

import java.time._
import java.util.Locale
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EmailValidationOrchestrator(
    mailService: MailServiceInterface,
    emailValidationRepository: EmailValidationRepositoryInterface,
    emailConfiguration: EmailConfiguration,
    messagesApi: MessagesApi,
    consumerConsentRepository: ConsumerConsentRepositoryInterface
)(implicit
    executionContext: ExecutionContext
) {

  private[this] val logger = Logger(this.getClass)

  def isEmailValid(email: EmailAddress): Future[Boolean] =
    for {
      emailValidation <- emailValidationRepository.findByEmail(email)
    } yield emailValidation.exists(_.isValid)

  def removeConsent(emailAddress: EmailAddress) = consumerConsentRepository.removeConsent(emailAddress)

  def checkCodeAndValidateEmail(emailValidationBody: ValidateEmailCode): Future[EmailValidationResult] =
    for {
      maybeEmailValidation <- emailValidationRepository.findByEmail(emailValidationBody.email)
      emailValidation <- maybeEmailValidation.liftTo[Future] {
        logger.warn(s"Email ${emailValidationBody.email.value} to validate not found")
        AppError.EmailOrCodeIncorrect(emailValidationBody.email)
      }
      _ = logger.debug("validating code")
      result <- checkCodeAndValidate(emailValidationBody, emailValidation)
    } yield result

  def checkEmail(email: EmailAddress, locale: Option[Locale]): Future[EmailValidationResult] = for {
    _ <- validateProvider(email)
    result <-
      if (emailConfiguration.skipReportEmailValidation)
        validateFormat(email)
      else
        sendValidationEmailIfNeeded(email, locale)
  } yield result

  def validateEmail(email: EmailAddress): Future[EmailValidationResult] =
    emailValidationRepository.validate(email).map { _ =>
      logger.debug("Email validated")
      EmailValidationResult.success
    }

  private[this] def checkCodeAndValidate(emailValidationBody: ValidateEmailCode, emailValidation: EmailValidation) =
    if (emailValidation.confirmationCode == emailValidationBody.confirmationCode) {
      validateEmailAndSaveConsent(emailValidationBody)
    } else
      emailValidationRepository
        .update(
          emailValidation.copy(
            attempts = emailValidation.attempts + 1,
            lastAttempt = Some(OffsetDateTime.now())
          )
        )
        .map { _ =>
          logger.debug("Invalid code")
          EmailValidationResult.invalidCode
        }

  private def validateEmailAndSaveConsent(emailValidationBody: ValidateEmailCode) =
    for {
      emailValidationResult <- validateEmail(emailValidationBody.email)
      _                     <- eventuallySaveConsent(emailValidationBody)
    } yield emailValidationResult

  private[this] def eventuallySaveConsent(emailCodeValidation: ValidateEmailCode) =
    if (emailCodeValidation.consentToUseData.contains(true)) {
      consumerConsentRepository.create(ConsumerConsent.create(emailCodeValidation.email))
    } else { Future.unit }

  private[this] def sendValidationEmailIfNeeded(
      email: EmailAddress,
      locale: Option[Locale]
  ): Future[EmailValidationResult] =
    for {
      emailValidation <- findOrCreate(email)
      res <-
        if (isEmailNotValidatedOrOutdated(emailValidation.lastValidationDate)) {
          logger.debug(s"Email ${emailValidation.email} not validated our outdated, sending email")
          mailService
            .send(ConsumerValidateEmail.Email(emailValidation, locale, messagesApi))
            .map(_ => EmailValidationResult.failure)
        } else {
          logger.debug(s"Email validated")
          Future.successful(EmailValidationResult.success)
        }
    } yield res

  private def isEmailNotValidatedOrOutdated(maybeValidationDate: Option[OffsetDateTime]) =
    maybeValidationDate match {
      case Some(validationDate) => validationDate.isBefore(EmailValidationThreshold)
      case None                 => true
    }

  def validateProvider(email: EmailAddress): Future[Unit] =
    if (emailConfiguration.emailProvidersBlocklist.exists(email.value.contains(_))) {
      Future.failed(AppError.InvalidEmailProvider)
    } else {
      Future.unit
    }

  private[this] def validateFormat(emailAddress: EmailAddress): Future[EmailValidationResult] = {
    logger.debug(s"Checking if email match EmailStringRegex.type regexp ")
    RefType
      .applyRef[EmailString](emailAddress.value)
      .fold(
        _ => Future.failed(AppError.InvalidEmail(emailAddress.value)),
        _ => Future.successful(EmailValidationResult.success)
      )
  }

  private[this] def findOrCreate(email: EmailAddress): Future[EmailValidation] =
    emailValidationRepository.findByEmail(email).flatMap {
      case None =>
        logger.debug(s"Unknown email , creating validation entry")
        for {
          _      <- if (emailConfiguration.extendedComparison) validateValidationSpamToday(email) else Future.unit
          result <- emailValidationRepository.create(EmailValidation(email = email))
        } yield result
      case Some(foundEmail) =>
        logger.debug(s"Found email in validation email table ")
        Future.successful(foundEmail)
    }

  private def validateValidationSpamToday(email: EmailAddress): Future[Unit] = {
    val today = ZonedDateTime.of(LocalDate.now(), LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
    emailValidationRepository.findSimilarEmail(email, today).flatMap {
      case None =>
        logger.debug(s"No email validation spam detected")
        Future.unit
      case Some(foundEmail) =>
        logger.debug(s"Found email validation spam for $email : ${foundEmail.email}")
        Future.failed(AppError.SpamDetected(email, foundEmail.email))
    }
  }

  def search(search: EmailValidationFilter, paginate: PaginatedSearch): Future[PaginatedResult[EmailValidationApi]] =
    emailValidationRepository
      .search(search, paginate)
      .map(_.mapEntities(EmailValidationApi.fromEmailValidation))
}
