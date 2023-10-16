package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models.BlacklistedEmail
import models.BlacklistedEmailInput
import models.UserPermission
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.blacklistedemails.BlacklistedEmailsRepositoryInterface
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BlacklistedEmailsController(
    blacklistedEmailsRepository: BlacklistedEmailsRepositoryInterface,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def list = SecuredAction(WithPermission(UserPermission.manageBlacklistedEmails)).async {
    for {
      res <- blacklistedEmailsRepository.list()
    } yield Ok(Json.toJson(res))
  }

  def add() = SecuredAction(WithPermission(UserPermission.manageBlacklistedEmails)).async(parse.json) {
    implicit request =>
      request.body
        .validate[BlacklistedEmailInput]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          input =>
            for {
              alreadyBlacklisted <- blacklistedEmailsRepository.isBlacklisted(input.email)
              result <- {
                if (alreadyBlacklisted) Future.successful(BadRequest("Email already blacklisted"))
                else
                  for {
                    _ <- blacklistedEmailsRepository.create(BlacklistedEmail.fromInput(input))
                  } yield Ok
              }
            } yield result
        )

  }

  def delete(uuid: UUID) = SecuredAction(WithPermission(UserPermission.manageBlacklistedEmails)).async {
    for {
      item <- blacklistedEmailsRepository.get(uuid)
      _    <- blacklistedEmailsRepository.delete(uuid)
    } yield if (item.isDefined) Ok else NotFound
  }

}
