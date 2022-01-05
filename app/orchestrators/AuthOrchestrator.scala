package orchestrators

import cats.implicits.catsSyntaxMonadError
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import controllers.error.AppError.DGCCRFUserEmailValidationExpired
import controllers.error.AppError.TooMuchAuthAttempts
import controllers.error.AppError.UserNotFound
import models.User
import models.UserLogin
import models.UserRole
import repositories.UserRepository
import utils.silhouette.auth.UserService

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import cats.syntax.option._
import cats.instances.future.catsStdInstancesForFuture
import com.mohiva.play.silhouette.api.LoginInfo
import config.AppConfigLoader
import controllers.error.AppError
import models.token.UserSession
import orchestrators.AuthOrchestrator.AuthAttemptPeriod
import orchestrators.AuthOrchestrator.MaxAllowedAuthAttempts
import play.api.Logger

import java.time.OffsetDateTime
import java.time.Period

class AuthOrchestrator @Inject() (
    userService: UserService,
    userRepository: UserRepository,
    accessesOrchestrator: AccessesOrchestrator,
    appConfigLoader: AppConfigLoader
)(implicit
    ec: ExecutionContext
) {

  private val logger: Logger = Logger(this.getClass)
  private val dgccrfDelayBeforeRevalidation: Period = appConfigLoader.get.token.dgccrfDelayBeforeRevalidation

  def login(userLogin: UserLogin, token: String): Future[UserSession] = {

    val eventualUserSession: Future[UserSession] = for {
      maybeUser <- userService.retrieve(LoginInfo(CredentialsProvider.ID, userLogin.login))
      user <- maybeUser.liftTo[Future](UserNotFound(userLogin.login))
      _ = logger.debug(s"Found user")
      _ = logger.debug(s"Validate auth attempts count")
      _ <- validateAuthenticationAttempts(user)
      _ = logger.debug(s"Check last validation email for DGCCRF users")
      _ <- validateDGCCRFAccountLastEmailValidation(user)
      _ = logger.debug(s"Successful login for user ${userLogin.login}")
    } yield UserSession(token, user)

    eventualUserSession
      .flatMap { session =>
        userRepository.saveAuthAttempt(userLogin.login, isSuccess = true).map(_ => session)
      }
      .recoverWith {
        case error: AppError =>
          userRepository
            .saveAuthAttempt(userLogin.login, isSuccess = false, failureCause = Some(error.details))
            .flatMap(_ => Future.failed(error))
        case error =>
          userRepository
            .saveAuthAttempt(
              userLogin.login,
              isSuccess = false,
              failureCause = Some(s"Unexpected error : ${error.getMessage}")
            )
            .flatMap(_ => Future.failed(error))

      }

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
          OffsetDateTime.now
            .minus(dgccrfDelayBeforeRevalidation)
        )
      )

  private def validateAuthenticationAttempts(user: User): Future[User] = for {
    _ <- userRepository
      .countAuthAttempts(user.email.value, AuthAttemptPeriod)
      .ensure(TooMuchAuthAttempts(user.id))(attempts => attempts < MaxAllowedAuthAttempts)
    _ = logger.debug(s"Auth attempts count check successful")
  } yield user

}

object AuthOrchestrator {
  val AuthAttemptPeriod: Duration = 30 minutes
  val MaxAllowedAuthAttempts: Int = 15
}
