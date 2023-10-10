package orchestrators
import utils.Logs.RichLogger
import cats.implicits.catsSyntaxEq
import cats.implicits.catsSyntaxMonadError
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import controllers.error.AppError.DGCCRFUserEmailValidationExpired
import controllers.error.AppError.DeletedAccount
import controllers.error.AppError.InvalidPassword
import controllers.error.AppError.PasswordTokenNotFoundOrInvalid
import controllers.error.AppError.SamePasswordError
import controllers.error.AppError.ServerError
import controllers.error.AppError.TooMuchAuthAttempts
import controllers.error.AppError.UserNotFound
import models.User
import models.UserRole
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.UserService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import cats.syntax.option._
import cats.instances.future.catsStdInstancesForFuture
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.exceptions.InvalidPasswordException
import config.TokenConfiguration
import controllers.error.AppError
import models.auth.AuthAttempt
import models.auth.AuthToken
import models.auth.PasswordChange
import models.auth.UserCredentials
import models.auth.UserLogin
import models.auth.UserPassword
import models.auth.UserSession
import orchestrators.AuthOrchestrator.AuthAttemptPeriod
import orchestrators.AuthOrchestrator.MaxAllowedAuthAttempts
import orchestrators.AuthOrchestrator.authTokenExpiration
import orchestrators.AuthOrchestrator.toLoginInfo
import play.api.Logger
import play.api.mvc.Cookie
import play.api.mvc.Request
import repositories.authattempt.AuthAttemptRepositoryInterface
import repositories.authtoken.AuthTokenRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.Email.ResetPassword
import services.MailService
import utils.PasswordComplexityHelper

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import scala.util.Failure
import scala.util.Success

class AuthOrchestrator(
    userService: UserService,
    authAttemptRepository: AuthAttemptRepositoryInterface,
    userRepository: UserRepositoryInterface,
    accessesOrchestrator: AccessesOrchestrator,
    authTokenRepository: AuthTokenRepositoryInterface,
    tokenConfiguration: TokenConfiguration,
    credentialsProvider: CredentialsProvider,
    credentialsProviderForDeletedUsers: CredentialsProvider,
    mailService: MailService,
    val silhouette: Silhouette[AuthEnv]
)(implicit
    ec: ExecutionContext
) {

  private val logger: Logger = Logger(this.getClass)
  private val dgccrfDelayBeforeRevalidation: Period = tokenConfiguration.dgccrfDelayBeforeRevalidation

  private def handleDeletedUser(user: User, userLogin: UserCredentials): Future[Unit] =
    if (user.deletionDate.isDefined)
      credentialsProviderForDeletedUsers
        .authenticate(Credentials(userLogin.login, userLogin.password))
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

  def login(userLogin: UserCredentials, request: Request[_]): Future[UserSession] = {
    logger.debug(s"Validate auth attempts count")
    val eventualUserSession: Future[UserSession] = for {
      _ <- validateAuthenticationAttempts(userLogin.login)
      maybeUser <- userService.retrieveIncludingDeleted(toLoginInfo(userLogin.login))
      user <- maybeUser.liftTo[Future](UserNotFound(userLogin.login))
      _ = logger.debug(s"Found user (maybe deleted)")
      _ <- handleDeletedUser(user, userLogin)
      _ = logger.debug(s"Check last validation email for DGCCRF users")
      _ <- validateDGCCRFAccountLastEmailValidation(user)
      _ = logger.debug(s"Successful login for user")
      cookie <- getCookie(userLogin)(request)
      _ = logger.debug(s"Successful generated token for user")
    } yield UserSession(cookie, user)

    eventualUserSession
      .flatMap { session =>
        logger.debug(s"Saving auth attempts for user")
        authAttemptRepository.create(AuthAttempt.build(userLogin.login, isSuccess = true)).map(_ => session)
      }
      .recoverWith {
        case error: AppError =>
          logger.debug(s"Saving failed auth attempt for user")
          authAttemptRepository
            .create(AuthAttempt.build(userLogin.login, isSuccess = false, failureCause = Some(error.details)))
            .flatMap(_ => Future.failed(error))
        case error =>
          logger.debug(s"Saving failed auth attempt for user")
          authAttemptRepository
            .create(
              AuthAttempt.build(
                userLogin.login,
                isSuccess = false,
                failureCause = Some(s"Unexpected error : ${error.getMessage}")
              )
            )
            .flatMap(_ => Future.failed(error))

      }

  }

  def forgotPassword(resetPasswordLogin: UserLogin): Future[Unit] =
    userService.retrieve(toLoginInfo(resetPasswordLogin.login)).flatMap {
      case Some(user) =>
        for {
          _ <- authTokenRepository.deleteForUserId(user.id)
          _ = logger.debug(s"Creating auth token for ${user.id}")
          authToken <- authTokenRepository.create(AuthToken(UUID.randomUUID(), user.id, authTokenExpiration))
          _ = logger.debug(s"Sending reset email to ${user.id}")
          _ <- mailService.send(ResetPassword(user, authToken))
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
          _ <- Future(PasswordComplexityHelper.validatePasswordComplexity(userPassword.password))
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
    _ <- authenticate(user.email.value, passwordChange.oldPassword)
    _ <- Future(PasswordComplexityHelper.validatePasswordComplexity(passwordChange.newPassword))
    _ = logger.debug(s"Successfully checking old password  user id ${user.id}, updating password")
    _ <- userRepository.updatePassword(user.id, passwordChange.newPassword)
    _ = logger.debug(s"Password updated for user id ${user.id}")
  } yield ()

  private def getCookie(userLogin: UserCredentials)(implicit req: Request[_]): Future[Cookie] =
    for {
      loginInfo <- authenticate(userLogin.login, userLogin.password)
      authenticator <- silhouette.env.authenticatorService.create(loginInfo)
      cookie <- silhouette.env.authenticatorService.init(authenticator)
    } yield cookie

  private def authenticate(login: String, password: String) =
    credentialsProvider
      .authenticate(Credentials(login, password))
      .recoverWith {
        case e: InvalidPasswordException =>
          logger.warnWithTitle("invalid_password", "Invalid password ", e)
          Future.failed(InvalidPassword(login))
        case _: IdentityNotFoundException => Future.failed(UserNotFound(login))
        case err => Future.failed(ServerError("Unexpected error when authenticating user", Some(err)))
      }

  private def validateDGCCRFAccountLastEmailValidation(user: User): Future[User] = user.userRole match {
    case UserRole.DGCCRF if needsEmailRevalidation(user) =>
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
      )

  private def validateAuthenticationAttempts(login: String): Future[Unit] = for {
    _ <- authAttemptRepository
      .countAuthAttempts(login, AuthAttemptPeriod)
      .ensure(TooMuchAuthAttempts(login))(attempts => attempts < MaxAllowedAuthAttempts)
    _ = logger.debug(s"Auth attempts count check successful")
  } yield ()

}

object AuthOrchestrator {
  val AuthAttemptPeriod: Duration = 30 minutes
  val MaxAllowedAuthAttempts: Int = 20
  def authTokenExpiration: OffsetDateTime = OffsetDateTime.now().plusDays(1)
  def toLoginInfo(login: String): LoginInfo = LoginInfo(CredentialsProvider.ID, login)
}
