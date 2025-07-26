package authentication

import cats.implicits.toTraverseOps
import controllers.error.AppError.BrokenAuthError
import models.Consumer
import play.api.Logger
import play.api.mvc.Request
import repositories.consumer.ConsumerRepositoryInterface

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class APIKeyAuthenticator(
    passwordHasherRegistry: PasswordHasherRegistry,
    consumerRepository: ConsumerRepositoryInterface,
    apiConsumerTokenExpirationDelayInMonths: Int
)(implicit ec: ExecutionContext)
    extends Authenticator[Consumer] {

  val logger: Logger = Logger(this.getClass)

  private val XApiKey = "X-Api-Key"

  override def authenticate[B](request: Request[B]): Future[Either[BrokenAuthError, Option[Consumer]]] = {
    val hasher         = passwordHasherRegistry.current
    val headerValueOpt = request.headers.get(XApiKey)

    headerValueOpt
      .traverse { headerValue =>
        consumerRepository
          .getAll()
          .map(
            _.find(c => hasher.matches(Credentials.toPasswordInfo(c.apiKey), headerValue))
              .toRight(
                BrokenAuthError(
                  s"Access denied to the external API, invalid $XApiKey header when calling ${request.uri}."
                )
              )
              .flatMap(validateExpiration)
          )
      }
      .map(handleMissingHeader(request, _))
  }

  private def validateExpiration(c: Consumer) =
    if (c.creationDate.isBefore(OffsetDateTime.now().minusMonths(apiConsumerTokenExpirationDelayInMonths))) {
      Left(BrokenAuthError("Expired credentials, please contact the support to generate a new API Key."))
    } else {
      logger.info(s"Access to the API with token ${c.name}.")
      Right(c)
    }

  private def handleMissingHeader[B](request: Request[B], maybeHeader: Option[Either[BrokenAuthError, Consumer]]) =
    maybeHeader.map(_.map(Some(_))).getOrElse {
      logger.error(
        s"Access denied to the external API, missing $XApiKey header when calling ${request.uri}."
      )
      Left(
        BrokenAuthError(s"Access denied to the external API, missing $XApiKey header when calling ${request.uri}.")
      )
    }

}
