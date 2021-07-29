package utils.silhouette.api

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.RequestProvider
import javax.inject.Inject
import play.api.Configuration
import play.api.mvc.Request

import scala.concurrent.Future
import utils.silhouette.Implicits._

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
        case (Some(headerValue), secretValue) if hasher.matches(headerValue, secretValue) =>
          Some(LoginInfo(id, headerValue))
        case _ => None
      }
    )
  }

  override def id = "api-key"
}
