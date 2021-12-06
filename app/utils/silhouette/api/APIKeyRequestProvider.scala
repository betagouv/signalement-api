package utils.silhouette.api

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.RequestProvider
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import play.api.{Configuration, Logger}
import play.api.mvc.Request
import repositories.ConsumerRepository
import utils.silhouette.Credentials._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class APIKeyRequestProvider @Inject() (
    passwordHasherRegistry: PasswordHasherRegistry,
    _consumer: ConsumerRepository
)(implicit ec: ExecutionContext) extends RequestProvider {

  val logger: Logger = Logger(this.getClass)

  def authenticate[B](request: Request[B]): Future[Option[LoginInfo]] = {
    val hasher = passwordHasherRegistry.current
    val headerValueOpt = request.headers.get("X-Api-Key")

    headerValueOpt.map(headerValue =>
      _consumer.getAll().map { consumers =>
        val keyMatchOpt = consumers.find(c => hasher.matches(toPasswordInfo(headerValueOpt.get), c.apiKey))
        keyMatchOpt match {
          case Some(keyMatch) if hasher.matches(toPasswordInfo(headerValue), keyMatch.apiKey) => {
            logger.debug(s"Access to the API with token ${keyMatch.name}.")
            Some(LoginInfo(id, headerValue))
          }
          case _ => None
        }
      }
    )
    .getOrElse(Future.successful(None))
  }

  override def id = "api-key"
}
