package controllers

import authentication.Authenticator
import models.SubscriptionCreation
import models.SubscriptionUpdate
import models.User
import orchestrators.SubscriptionOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubscriptionController(
    subscriptionOrchestrator: SubscriptionOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def createSubscription =
    Act.secured.adminsAndReadonlyAndAgents.forbidImpersonation
      .async(parse.json) { implicit request =>
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
    Act.secured.adminsAndReadonlyAndAgents.forbidImpersonation
      .async(parse.json) { implicit request =>
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

  def getSubscriptions =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { implicit request =>
      subscriptionOrchestrator.getSubscriptions(request.identity).map(subscriptions => Ok(Json.toJson(subscriptions)))
    }

  def getSubscription(uuid: UUID) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { implicit request =>
      subscriptionOrchestrator
        .getSubscription(uuid, request.identity)
        .map(_.map(s => Ok(Json.toJson(s))).getOrElse(NotFound))
    }

  def removeSubscription(uuid: UUID) =
    Act.secured.adminsAndReadonlyAndAgents.forbidImpersonation.async { implicit request =>
      subscriptionOrchestrator
        .removeSubscription(uuid, request.identity)
        .map(deletedCount => if (deletedCount > 0) Ok else NotFound)

    }
}
