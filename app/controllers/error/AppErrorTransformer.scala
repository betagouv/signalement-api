package controllers.error

import controllers.error.AppError.ServerError
import controllers.error.ErrorPayload.AuthenticationErrorPayload
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results

object AppErrorTransformer {

  val logger: Logger = Logger(this.getClass())

  def handleError(err: Throwable): Result =
    err match {
      case appError: AppError => handleError(appError)
      case err =>
        logger.error("Encountered unexpected error", err)
        Results.InternalServerError(Json.toJson(ErrorPayload(ServerError("Encountered unexpected error", Some(err)))))
    }

  private def handleError(error: AppError): Result =
    error match {
      case error: NotFoundError =>
        logger.info(error.details)
        Results.NotFound(Json.toJson(ErrorPayload(error)))

      case error: PreconditionError =>
        logger.warn(error.details)
        Results.PreconditionFailed(Json.toJson(ErrorPayload(error)))

      case error: ConflictError =>
        logger.warn(error.details)
        Results.Conflict(Json.toJson(ErrorPayload(error)))

      case error: BadRequestError =>
        logger.warn(error.details)
        Results.BadRequest(Json.toJson(ErrorPayload(error)))

      case error: ForbiddenError =>
        logger.warn(error.details)
        Results.Forbidden(Json.toJson(ErrorPayload(error)))

      case error: InternalAppError =>
        logger.error(error.details, error)
        Results.InternalServerError(Json.toJson(ErrorPayload(error)))

      case error: UnauthorizedError =>
        logger.warn(error.details, error)
        Results.Unauthorized(Json.toJson(AuthenticationErrorPayload))
    }
}
