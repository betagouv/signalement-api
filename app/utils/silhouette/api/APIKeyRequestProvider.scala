package utils.silhouette.api

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.RequestProvider
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import play.api.Configuration
import play.api.mvc.Request
import utils.silhouette.Credentials._

import javax.inject.Inject
import scala.concurrent.Future

class APIKeyRequestProvider @Inject() (
    passwordHasherRegistry: PasswordHasherRegistry,
    configuration: Configuration
) extends RequestProvider {

  def authenticate[B](request: Request[B]): Future[Option[LoginInfo]] = {

    val hasher = passwordHasherRegistry.current

    Future.successful(
      (
        request.headers.get(configuration.get[String]("silhouette.apiKeyAuthenticator.headerName")),
        configuration.get[String]("silhouette.apiKeyAuthenticator.sharedSecret")
      ) match {
        case (Some(headerValue), secretValue)
            if hasher
              .matches(toPasswordInfo(headerValue), secretValue) =>
          Some(LoginInfo(id, headerValue))
        case _ => None
      }
    )
  }

  override def id = "api-key"
}
