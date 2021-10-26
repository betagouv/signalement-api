package controllers.error

import utils.EmailAddress
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
    override val details: String = "Une erreur inattendue s'est produite."
  }

  final case class DGCCRFActivationTokenNotFound(token: String) extends NotFoundError {
    override val `type`: String = "SC-0002"
    override val title: String = s"DGCCRF user token $token not found"
    override val details: String = "Le lien d'activation n'est pas valide. Merci de contacter le support"
  }

  final case class CompanyActivationTokenNotFound(token: String, siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0003"
    override val title: String = "Company user token $token with siret ${siret.value} not found"
    override val details: String = "Le lien d'activation n'est pas valide. Merci de contacter le support"
  }

  final case class CompanySiretNotFound(siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0004"
    override val title: String = s"Company siret not found"
    override val details: String = s"Le SIRET ${siret.value} ne correspond à aucune entreprise connue"
  }

  final case class WebsiteNotFound(websiteId: UUID) extends NotFoundError {
    override val `type`: String = "SC-0005"
    override val title: String = s"Website ${websiteId.toString} not found"
    override val details: String = "L'association site internet n'existe pas."
  }

  final case class CompanyAlreadyAssociatedToWebsite(websiteId: UUID, siret: SIRET) extends BadRequestError {
    override val `type`: String = "SC-0006"
    override val title: String = "Company already associated to website  ${websiteId.toString}"
    override val details: String =
      s"Le SIRET ${siret.value} est déjà associé au site internet"
  }

  final case class MalformedHost(host: String) extends BadRequestError {
    override val `type`: String = "SC-0007"
    override val title: String = "Malformed host"
    override val details: String =
      s"Le site $host doit être un site valide."
  }

  final case class InvalidDGCCRFEmail(email: EmailAddress, suffix: String) extends ForbiddenError {
    override val `type`: String = "SC-0008"
    override val title: String = "Invalid Dgccrf email"
    override val details: String =
      s"Email ${email.value} invalide. Email acceptés : *${suffix}"
  }

  final case object UserAccountEmailAlreadyExist extends BadRequestError {
    override val `type`: String = "SC-0009"
    override val title: String = "User already exist"
    override val details: String =
      s"Ce compte existe déjà. Merci de demander à l'utilisateur de regénérer son mot de passe pour se connecter"
  }

  final case class AlreadyActivatedCompany(siret: String) extends ForbiddenError {
    override val `type` = "SC-0010"
    override val title = "Already activated company"
    override val details = s"Company $siret has already been activated."
  }

  final case class CompanyToActivateNotFound(siret: String) extends NotFoundError {
    override val `type` = "SC-0011"
    override val title = "Company to activate not found"
    override val details = s"Company $siret to activate not found"
  }

  final case class CompanyToActivateTokenOutdated(siret: String) extends ForbiddenError {
    override val `type` = "SC-0012"
    override val title = "Company to activate token outdated"
    override val details = s"Company $siret to activate token outdated."
  }

  final case class CompanyToActivateInvalidToken(siret: String) extends ForbiddenError {
    override val `type` = "SC-0013"
    override val title = "Company to activate invalid token"
    override val details = s"Company $siret to activate invalid token."
  }
}
