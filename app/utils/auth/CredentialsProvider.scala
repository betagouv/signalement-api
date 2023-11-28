package utils.auth

import controllers.error.AppError.InvalidPassword
import controllers.error.AppError.ServerError
import controllers.error.AppError.UserNotFound
import models.User
import play.api.Logger
import repositories.user.UserRepositoryInterface
import utils.Logs.RichLogger
import utils.silhouette.Credentials

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CredentialsProvider(passwordHasherRegistry: PasswordHasherRegistry, userRepository: UserRepositoryInterface)(
    implicit ec: ExecutionContext
) {

  private val logger: Logger = Logger(this.getClass)

  private def authenticate(maybeUser: Option[User], login: String, password: String): Future[Unit] = {
    val passwordInfo = Credentials.toPasswordInfo(password)
    maybeUser match {
      case Some(user) =>
        passwordHasherRegistry.find(passwordInfo) match {
          case Some(hasher) if hasher.matches(passwordInfo, password) =>
            if (passwordHasherRegistry.isDeprecated(hasher) || hasher.isDeprecated(passwordInfo).contains(true)) {
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
