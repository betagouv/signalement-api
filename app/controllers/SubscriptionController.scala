package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.SubscriptionCreation
import models.SubscriptionUpdate
import models.UserPermission
import orchestrators.SubscriptionOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubscriptionController(
    subscriptionOrchestrator: SubscriptionOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def createSubscription = SecuredAction(WithPermission(UserPermission.subscribeReports)).async(parse.json) {
    implicit request =>
      request.body
        .validate[SubscriptionCreation]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          draftSubscription =>
            subscriptionOrchestrator
              .createSubscription(draftSubscription, request.identity)
              .map(subscription => Ok(Json.toJson(subscription)))
        )
  }

  def updateSubscription(uuid: UUID) =
    SecuredAction(WithPermission(UserPermission.subscribeReports)).async(parse.json) { implicit request =>
      request.body
        .validate[SubscriptionUpdate]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          draftSubscription =>
            subscriptionOrchestrator
              .updateSubscription(uuid, draftSubscription, request.identity)
              .map {
                case Some(updatedSubscription) => Ok(Json.toJson(updatedSubscription))
                case None                      => NotFound
              }
        )
    }

  def getSubscriptions = SecuredAction(WithPermission(UserPermission.subscribeReports)).async { implicit request =>
    subscriptionOrchestrator.getSubscriptions(request.identity).map(subscriptions => Ok(Json.toJson(subscriptions)))
  }

  def getSubscription(uuid: UUID) = SecuredAction(WithPermission(UserPermission.subscribeReports)).async {
    implicit request =>
      subscriptionOrchestrator
        .getSubscription(uuid, request.identity)
        .map(_.map(s => Ok(Json.toJson(s))).getOrElse(NotFound))
  }

  def removeSubscription(uuid: UUID) = SecuredAction(WithPermission(UserPermission.subscribeReports)).async {
    implicit request =>
      subscriptionOrchestrator
        .removeSubscription(uuid, request.identity)
        .map(deletedCount => if (deletedCount > 0) Ok else NotFound)

  }
}
