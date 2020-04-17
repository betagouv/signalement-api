package controllers

import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject._
import models.{DraftSubscription, Subscription, UserPermission}
import play.api.Logger
import play.api.libs.json.{JsError, Json}
import repositories.SubscriptionRepository
import utils.silhouette.auth.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SubscriptionController @Inject()(subscriptionRepository: SubscriptionRepository,
                                       val silhouette: Silhouette[AuthEnv])(implicit ec: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def subscribe = SecuredAction(WithPermission(UserPermission.subscribeReports)).async(parse.json) { implicit request =>

    request.body.validate[DraftSubscription].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      draftSubscription => subscriptionRepository.create(
        Subscription(
          UUID.randomUUID,
          Some(request.identity.id),
          None,
          draftSubscription.departments,
          draftSubscription.categories
        )
      ).flatMap(subscription => Future.successful(Ok(Json.toJson(subscription))))
    )
  }

  def getSubscriptions = SecuredAction(WithPermission(UserPermission.subscribeReports)).async { implicit request =>
    subscriptionRepository.list(request.identity.id).flatMap(subscriptions =>
      Future(Ok(Json.toJson(subscriptions)))
    )
  }

  def removeSubscription(uuid: UUID) = SecuredAction(WithPermission(UserPermission.subscribeReports)).async { implicit request =>
    for {
      subscriptions <- subscriptionRepository.list(request.identity.id)
      deletedCount <- subscriptions.filter(_.id == uuid).headOption.map(subscription => subscriptionRepository.delete(subscription.id)).getOrElse(Future(0))
    } yield if (deletedCount > 0) Ok else Unauthorized
  }
}
