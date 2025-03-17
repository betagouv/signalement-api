package authentication

import controllers.error.AppError.InvalidPassword
import controllers.error.AppError.ServerError
import controllers.error.AppError.UserNotFound
import models.User
import play.api.Logger
import repositories.user.UserRepositoryInterface
import utils.Logs.RichLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CredentialsProvider(passwordHasherRegistry: PasswordHasherRegistry, userRepository: UserRepositoryInterface)(
    implicit ec: ExecutionContext
) {

  private val logger: Logger = Logger(this.getClass)

  private def authenticate(maybeUser: Option[User], login: String, password: String): Future[Unit] =
    maybeUser match {
      case Some(user) if user.password.isEmpty =>
        logger.warnWithTitle("blank_password", "Invalid password")
        Future.failed(InvalidPassword(login))
      case Some(user) =>
        val storedPasswordInfo = Credentials.toPasswordInfo(user.password)
        passwordHasherRegistry.find(storedPasswordInfo) match {
          case Some(hasher) if hasher.matches(storedPasswordInfo, password) =>
            if (passwordHasherRegistry.isDeprecated(hasher) || hasher.isDeprecated(storedPasswordInfo).contains(true)) {
              userRepository.updatePassword(user.id, password).map(_ => ())
            } else {
              Future.unit
            }
          case Some(_) =>
            logger.warnWithTitle("invalid_password", "Invalid password")
            Future.failed(InvalidPassword(login))
          case None => Future.failed(ServerError("Unexpected error when authenticating user"))
        }
      case None =>
        Future.failed(UserNotFound(login))
    }

  def authenticate(login: String, password: String): Future[Unit] =
    userRepository.findByEmail(login).flatMap { maybeUser =>
      authenticate(maybeUser, login, password)
    }

  def authenticateIncludingDeleted(login: String, password: String): Future[Unit] =
    userRepository.findByEmailIncludingDeleted(login).flatMap { maybeUser =>
      authenticate(maybeUser, login, password)
    }
}
