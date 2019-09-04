package controllers

import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.{Subscription, UserPermission}
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.SubscriptionRepository
import utils.silhouette.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SubscriptionController @Inject()(subscriptionRepository: SubscriptionRepository,
                                       val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def subscribe = SecuredAction(WithPermission(UserPermission.subscribeReports)).async(parse.json) { implicit request =>

    request.body.validate[Subscription].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      subscription => {
        subscription.id match {
          case None => subscriptionRepository.create(
            subscription.copy(
              id = Some(UUID.randomUUID()),
              userId = Some(request.identity.id))
          ).flatMap(subscription => Future.successful(Ok(Json.toJson(subscription))))
          case Some(id) => subscriptionRepository.update(subscription).flatMap(subscription => Future.successful(Ok(Json.toJson(subscription))))
        }
      }
    )
  }

  def getSubscriptions = SecuredAction(WithPermission(UserPermission.subscribeReports)).async { implicit request =>

    subscriptionRepository.list(request.identity.id).flatMap(subscriptions =>
      Future(Ok(Json.toJson(subscriptions)))
    )

  }
}
