package controllers.error

import controllers.error.AppError.BrokenAuthError
import controllers.error.AppError.ServerError
import controllers.error.ErrorPayload.failedAuthenticationErrorPayload
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results
import utils.Logs.RichLogger

import java.util.UUID
object AppErrorTransformer {

  val logger: Logger = Logger(this.getClass())

  private def formatMessage[R <: Request[_]](request: R, maybeUser: Option[UUID], appError: AppError): String =
    formatMessage(request, maybeUser, appError.messageInLogs)

  private def formatMessage[R <: Request[_]](request: R, maybeUser: Option[UUID], message: String): String =
    s"$message (User ${maybeUser.getOrElse("not connected")}, uri ${request.uri})"

  def handleError[R <: Request[_]](request: R, err: Throwable, maybeUserId: Option[UUID] = None): Result =
    err match {
      case appError: AppError =>
        handleAppError(request, appError, maybeUserId)
      case err =>
        logger.errorWithTitle(
          "global_handler_unexpected_error",
          formatMessage(request, maybeUserId, "Unexpected error occured"),
          err
        )
        Results.InternalServerError(Json.toJson(ErrorPayload(ServerError("Encountered unexpected error", Some(err)))))
    }

  private def handleAppError[R <: Request[_]](request: R, error: AppError, maybeUserId: Option[UUID]): Result =
    error match {
      case error: NotFoundError =>
        logger.warnWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error))
        Results.NotFound(Json.toJson(ErrorPayload(error)))

      case error: PreconditionError =>
        logger.warnWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error))
        Results.PreconditionFailed(Json.toJson(ErrorPayload(error)))

      case error: ConflictError =>
        logger.warnWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error))
        Results.Conflict(Json.toJson(ErrorPayload(error)))

      case error: BadRequestError =>
        logger.warnWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error))
        Results.BadRequest(Json.toJson(ErrorPayload(error)))

      case error: MalformedApiBadRequestError =>
        logger.errorWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error))
        Results.BadRequest(Json.toJson(ErrorPayload(error)))

      case error: ForbiddenError =>
        logger.warnWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error))
        Results.Forbidden(Json.toJson(ErrorPayload(error)))

      case error: InternalAppError =>
        logger.errorWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error), error)
        Results.InternalServerError(Json.toJson(ErrorPayload(error)))

      case error: BrokenAuthError =>
        logger.warnWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error), error)
        Results.Unauthorized(
          Json.toJson(
            ErrorPayload(
              "SC-AUTH-BROKEN",
              "Broken authentication",
              s"Vous êtes déconnecté : ${error.clientSideMessage}"
            )
          )
        )
      case error: FailedAuthenticationError =>
        logger.warnWithTitle(error.titleForLogs, formatMessage(request, maybeUserId, error), error)
        Results.Unauthorized(Json.toJson(failedAuthenticationErrorPayload))
    }
}
