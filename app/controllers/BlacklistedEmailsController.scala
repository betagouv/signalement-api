package controllers

import authentication.Authenticator
import models.BlacklistedEmail
import models.BlacklistedEmailInput
import models.User
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.blacklistedemails.BlacklistedEmailsRepositoryInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BlacklistedEmailsController(
    blacklistedEmailsRepository: BlacklistedEmailsRepositoryInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController(authenticator, controllerComponents) {
  val logger: Logger = Logger(this.getClass)

  def list = Act.secured.adminsAndReadonly.async {
    for {
      res <- blacklistedEmailsRepository.list()
    } yield Ok(Json.toJson(res))
  }

  def add() = Act.secured.admins.async(parse.json) { implicit request =>
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

  def delete(uuid: UUID) = Act.secured.admins.async {
    for {
      item <- blacklistedEmailsRepository.get(uuid)
      _    <- blacklistedEmailsRepository.delete(uuid)
    } yield if (item.isDefined) Ok else NotFound
  }

}
