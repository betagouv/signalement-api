package utils

import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n.I18nSupport
import play.api.i18n.MessagesApi
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router

import scala.concurrent.Future

@Singleton
class ErrorHandler @Inject() (
    env: Environment,
    config: Configuration,
    sourceMapper: OptionalSourceMapper,
    router: Provider[Router],
    val messagesApi: MessagesApi
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
    with SecuredErrorHandler
    with UnsecuredErrorHandler
    with I18nSupport {

  override def onNotAuthenticated(implicit request: RequestHeader): Future[Result] = Future.successful {
    Unauthorized
  }

  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = Future.successful {
    Forbidden
  }

  override def onNotFound(request: RequestHeader, message: String): Future[Result] = Future.successful {
    NotFound
  }

  override def onProdServerError(request: RequestHeader, exception: UsefulException) = Future.successful {
    InternalServerError
  }
}
