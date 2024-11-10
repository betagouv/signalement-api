package orchestrators

import authentication.CookieAuthenticator
import authentication.CredentialsProvider
import authentication.actions.IdentifiedRequest
import cats.implicits.catsSyntaxEq
import cats.implicits.catsSyntaxMonadError
import cats.instances.future.catsStdInstancesForFuture
import cats.syntax.option._
import config.TokenConfiguration
import controllers.error.AppError
import controllers.error.AppError._
import models.AuthProvider
import models.PaginatedResult
import models.User
import models.UserRole
import models.auth._
import orchestrators.AuthOrchestrator.AuthAttemptPeriod
import orchestrators.AuthOrchestrator.MaxAllowedAuthAttempts
import orchestrators.AuthOrchestrator.authTokenExpiration
import play.api.Logger
import repositories.authattempt.AuthAttemptRepositoryInterface
import repositories.authtoken.AuthTokenRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsVarious.ResetPassword
import services.emails.MailService
import utils.Logs.RichLogger
import utils.EmailAddress
import utils.PasswordComplexityHelper
import cats.syntax.either._
import models.AuthProvider.SignalConso

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

class AuthOrchestrator(
    authAttemptRepository: AuthAttemptRepositoryInterface,
    userRepository: UserRepositoryInterface,
    accessesOrchestrator: AccessesOrchestrator,
    authTokenRepository: AuthTokenRepositoryInterface,
    tokenConfiguration: TokenConfiguration,
    credentialsProvider: CredentialsProvider,
    mailService: MailService,
    authenticator: CookieAuthenticator
)(implicit
    ec: ExecutionContext
) {

  private val logger: Logger                        = Logger(this.getClass)
  private val dgccrfDelayBeforeRevalidation: Period = tokenConfiguration.dgccrfDelayBeforeRevalidation

  private def handleDeletedUser(user: User, userLogin: UserCredentials): Future[Unit] =
    if (user.deletionDate.isDefined)
      credentialsProvider
        .authenticateIncludingDeleted(userLogin.login, userLogin.password)
        .transformWith {
          case Success(_) =>
            logger.debug(s"Found a deleted user with right credentials, returning 'deleted account'")
            Future.failed(DeletedAccount(userLogin.login))
          case Failure(_) =>
            logger.debug(s"Found a deleted user with bad credentials, returning 'user not found'")
            Future.failed(UserNotFound(userLogin.login))
        }
    else {
      logger.debug(s"User is not deleted")
      Future.successful(())
    }

  def signalConsoLogin(userCredentials: UserCredentials): Future[UserSession] = {

    val eventualUserSession = for {
      _    <- validateAuthenticationAttempts(userCredentials.login)
      user <- getStrictUser(userCredentials.login)
      _ = logger.debug(s"Found user (maybe deleted)")
      _ <- handleDeletedUser(user, userCredentials)
      _ = logger.debug(s"Check last validation email for DGCCRF users")
      _ <- validateAgentAccountLastEmailValidation(user)
      _ = logger.debug(s"Successful login for user")
      _      <- credentialsProvider.authenticate(userCredentials.login, userCredentials.password)
      cookie <- authenticator.initSignalConsoCookie(EmailAddress(userCredentials.login), None).liftTo[Future]
      _ = logger.debug(s"Successful generated token for user")
    } yield UserSession(cookie, user)

    saveAuthAttemptWithRecovery(userCredentials.login, eventualUserSession)

  }

  def proConnectLogin(
      user: User,
      proConnectIdToken: String,
      proConnectState: String
  ): Future[UserSession] = {
    val eventualUserSession = for {
      cookie <- authenticator
        .initProConnectCookie(user.email, proConnectIdToken, proConnectState)
        .liftTo[Future]
      _ = logger.debug(s"Successful generated token for user ${user.email.value}")
    } yield UserSession(cookie, user)

    saveAuthAttemptWithRecovery(user.email.value, eventualUserSession)
  }

  private def saveAuthAttemptWithRecovery[T](login: String, eventualSession: Future[T]): Future[T] =
    eventualSession
      .flatMap { session =>
        logger.debug(s"Saving auth attempts for user")
        authAttemptRepository.create(AuthAttempt.build(login, isSuccess = true)).map(_ => session)
      }
      .recoverWith {
        case error: AppError =>
          logger.debug(s"Saving failed auth attempt for user")
          authAttemptRepository
            .create(AuthAttempt.build(login, isSuccess = false, failureCause = Some(error.details)))
            .flatMap(_ => Future.failed(error))
        case error =>
          logger.debug(s"Saving failed auth attempt for user")
          authAttemptRepository
            .create(
              AuthAttempt.build(
                login,
                isSuccess = false,
                failureCause = Some(s"Unexpected error : ${error.getMessage}")
              )
            )
            .flatMap(_ => Future.failed(error))
      }

  private def getStrictUser(login: String) = for {
    maybeUser <- userRepository.findByEmailIncludingDeleted(login)
    user      <- maybeUser.liftTo[Future](UserNotFound(login))
  } yield user

  def logAs(userEmail: EmailAddress, request: IdentifiedRequest[User, _]) = for {
    maybeUserToImpersonate <- userRepository.findByEmail(userEmail.value)
    userToImpersonate      <- maybeUserToImpersonate.liftTo[Future](UserNotFound(userEmail.value))
    _ <- userToImpersonate.userRole match {
      case UserRole.Professionnel => Future.unit
      case _                      => Future.failed(BrokenAuthError("Not a pro"))
    }
    cookie <- authenticator.initSignalConsoCookie(userEmail, Some(request.identity.email)).liftTo[Future]
  } yield UserSession(cookie, userToImpersonate.copy(impersonator = Some(request.identity.email)))

  def logoutAs(userEmail: EmailAddress) = for {
    maybeUser <- userRepository.findByEmail(userEmail.value)
    user      <- maybeUser.liftTo[Future](UserNotFound(userEmail.value))
    cookie    <- authenticator.initSignalConsoCookie(userEmail, None).liftTo[Future]
  } yield UserSession(cookie, user)

  private def findSignalConsoUserByEmail(emailAddress: String) =
    userRepository.findByEmail(emailAddress).map(_.filter(_.authProvider == AuthProvider.SignalConso))

  def forgotPassword(resetPasswordLogin: UserLogin): Future[Unit] =
    findSignalConsoUserByEmail(resetPasswordLogin.login).flatMap {
      case Some(user) =>
        for {
          _ <- authTokenRepository.deleteForUserId(user.id)
          _ = logger.debug(s"Creating auth token for ${user.id}")
          authToken <- authTokenRepository.create(AuthToken(UUID.randomUUID(), user.id, authTokenExpiration))
          _ = logger.debug(s"Sending reset email to ${user.id}")
          _ <- mailService.send(ResetPassword.Email(user, authToken))
        } yield ()
      case _ =>
        logger.warnWithTitle("reset_pwd_user_not_found", "User not found, cannot reset password")
        Future.successful(())
    }

  def resetPassword(token: UUID, userPassword: UserPassword): Future[Unit] =
    authTokenRepository.findValid(token).flatMap {
      case Some(authToken) =>
        logger.debug(s"Found token for user id ${authToken.userID}")
        for {
          _ <- PasswordComplexityHelper.validatePasswordComplexity(userPassword.password)
          _ <- userRepository.updatePassword(authToken.userID, userPassword.password)
          _ = logger.debug(s"Password updated successfully for user id ${authToken.userID}")
          _ <- authTokenRepository.deleteForUserId(authToken.userID)
          _ = logger.debug(s"Token deleted successfully for user id ${authToken.userID}")
        } yield ()
      case None =>
        val error = PasswordTokenNotFoundOrInvalid(token)
        logger.warn(error.title)
        Future.failed(error)
    }

  def changePassword(user: User, passwordChange: PasswordChange) = for {
    _ <-
      if (passwordChange.oldPassword === passwordChange.newPassword) {
        Future.failed(SamePasswordError)
      } else {
        Future.unit
      }
    _ <- credentialsProvider.authenticate(user.email.value, passwordChange.oldPassword)
    _ <- PasswordComplexityHelper.validatePasswordComplexity(passwordChange.newPassword)
    _ = logger.debug(s"Successfully checking old password  user id ${user.id}, updating password")
    _ <- userRepository.updatePassword(user.id, passwordChange.newPassword)
    _ = logger.debug(s"Password updated for user id ${user.id}")
  } yield ()

  private def validateAgentAccountLastEmailValidation(user: User): Future[User] = user.userRole match {
    case UserRole.DGCCRF | UserRole.DGAL if needsEmailRevalidation(user) =>
      accessesOrchestrator
        .sendEmailValidation(user)
        .flatMap(_ => throw DGCCRFUserEmailValidationExpired(user.email.value))
    case _ =>
      logger.debug(s"No periodic email revalidation needed for the user")
      Future.successful(user)
  }

  private def needsEmailRevalidation(user: User) =
    user.lastEmailValidation
      .exists(
        _.isBefore(
          OffsetDateTime
            .now()
            .minus(dgccrfDelayBeforeRevalidation)
        )
      ) && user.authProvider == SignalConso

  private def validateAuthenticationAttempts(login: String): Future[Unit] = for {
    _ <- authAttemptRepository
      .countAuthAttempts(login, AuthAttemptPeriod)
      .ensure(TooMuchAuthAttempts(login))(attempts => attempts < MaxAllowedAuthAttempts)
    _ = logger.debug(s"Auth attempts count check successful")
  } yield ()

  def listAuthenticationAttempts(
      login: Option[String],
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[AuthAttempt]] =
    for {
      authAttempts <- authAttemptRepository.listAuthAttempts(login, offset, limit)
    } yield authAttempts

}

object AuthOrchestrator {
  val AuthAttemptPeriod: Duration         = 30 minutes
  val MaxAllowedAuthAttempts: Int         = 20
  def authTokenExpiration: OffsetDateTime = OffsetDateTime.now().plusDays(1)
}
