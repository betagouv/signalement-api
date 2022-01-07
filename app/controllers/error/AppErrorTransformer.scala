package controllers.error

import controllers.error.AppError.ServerError
import controllers.error.ErrorPayload.AuthenticationErrorPayload
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results

import java.util.UUID

object AppErrorTransformer {

  val logger: Logger = Logger(this.getClass())

  private def formatMessage(maybeUser: Option[UUID], appError: AppError) =
    s"""[${maybeUser.getOrElse("not_connected")}] ${appError.details}"""

  def handleError(err: Throwable, maybeUserId: Option[UUID] = None): Result =
    err match {
      case appError: AppError => handleError(appError, maybeUserId)
      case err =>
        logger.error("Encountered unexpected error", err)
        Results.InternalServerError(Json.toJson(ErrorPayload(ServerError("Encountered unexpected error", Some(err)))))
    }

  private def handleError(error: AppError, maybeUserId: Option[UUID]): Result =
    error match {
      case error: NotFoundError =>
        logger.warn(formatMessage(maybeUserId, error))
        Results.NotFound(Json.toJson(ErrorPayload(error)))

      case error: PreconditionError =>
        logger.warn(formatMessage(maybeUserId, error))
        Results.PreconditionFailed(Json.toJson(ErrorPayload(error)))

      case error: ConflictError =>
        logger.warn(formatMessage(maybeUserId, error))
        Results.Conflict(Json.toJson(ErrorPayload(error)))

      case error: BadRequestError =>
        logger.warn(formatMessage(maybeUserId, error))
        Results.BadRequest(Json.toJson(ErrorPayload(error)))

      case error: ForbiddenError =>
        logger.warn(formatMessage(maybeUserId, error))
        Results.Forbidden(Json.toJson(ErrorPayload(error)))

      case error: InternalAppError =>
        logger.error(formatMessage(maybeUserId, error), error)
        Results.InternalServerError(Json.toJson(ErrorPayload(error)))

      case error: UnauthorizedError =>
        logger.warn(formatMessage(maybeUserId, error), error)
        Results.Unauthorized(Json.toJson(AuthenticationErrorPayload))
    }
}
