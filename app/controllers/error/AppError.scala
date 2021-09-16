package controllers.error

import utils.SIRET

import java.util.UUID

sealed trait AppError extends Throwable with Product with Serializable {
  val `type`: String
  val title: String
  val details: String
}

sealed trait NotFoundError extends AppError
sealed trait BadRequestError extends AppError
sealed trait ForbiddenError extends AppError
sealed trait ConflictError extends AppError
sealed trait InternalAppError extends AppError
sealed trait PreconditionError extends AppError

object AppError {

  final case class ServerError(message: String, cause: Option[Throwable] = None) extends InternalAppError {
    override val `type`: String = "SC-0001"
    override val title: String = "Unexpected error"
    override val details: String = "An unexpected error occurred"
  }

  final case class DGCCRFActivationTokenNotFound(token: String) extends NotFoundError {
    override val `type`: String = "SC-0002"
    override val title: String = "DGCCRF user token not found"
    override val details: String = s"DGCCRF user token $token could not be found"
  }

  final case class CompanyActivationTokenNotFound(token: String, siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0003"
    override val title: String = "Company user token not found"
    override val details: String = s"Company user token $token with siret ${siret.value} could not be found"
  }

  final case class CompanySiretNotFound(siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0004"
    override val title: String = "Company siret not found"
    override val details: String = s"Company with siret ${siret.value} could not be found"
  }

  final case class WebsiteNotFound(websiteId: UUID) extends NotFoundError {
    override val `type`: String = "SC-0005"
    override val title: String = "Website not found"
    override val details: String = s"Website with id ${websiteId} could not be found"
  }

}
