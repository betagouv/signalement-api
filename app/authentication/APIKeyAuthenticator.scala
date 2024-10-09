package authentication

import controllers.error.AppError.BrokenAuthError
import models.Consumer
import play.api.Logger
import play.api.mvc.Request
import repositories.consumer.ConsumerRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class APIKeyAuthenticator(
    passwordHasherRegistry: PasswordHasherRegistry,
    consumerRepository: ConsumerRepositoryInterface
)(implicit ec: ExecutionContext)
    extends Authenticator[Consumer] {

  val logger: Logger = Logger(this.getClass)

  override def authenticate[B](request: Request[B]): Future[Either[BrokenAuthError, Option[Consumer]]] = {
    val hasher         = passwordHasherRegistry.current
    val headerValueOpt = request.headers.get("X-Api-Key")

    headerValueOpt
      .map { headerValue =>
        consumerRepository.getAll().map { consumers =>
          val keyMatchOpt = consumers.find { c =>
            hasher.matches(Credentials.toPasswordInfo(c.apiKey), headerValue)
          }
          keyMatchOpt match {
            case Some(consumer) =>
              logger.info(s"Access to the API with token ${consumer.name}.")
              Some(consumer)
            case _ =>
              logger.error(
                s"Access denied to the external API, invalid X-Api-Key header when calling ${request.uri}."
              )
              None
          }
        }
      }
      .getOrElse {
        logger.error(
          s"Access denied to the external API, missing X-Api-Key header when calling ${request.uri}."
        )
        Future.successful(None)
      }
      .map(Right(_))
  }
}
