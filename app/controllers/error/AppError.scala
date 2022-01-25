package controllers.error

import utils.EmailAddress
import utils.SIRET

import java.util.UUID
import scala.util.control.NoStackTrace

sealed trait AppError extends Throwable with Product with Serializable with NoStackTrace {
  val `type`: String
  val title: String
  val details: String
}

sealed trait UnauthorizedError extends AppError
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
    override val details: String = s"Le lien d'activation n'est pas valide ($token). Merci de contacter le support"
  }

  final case class CompanyActivationTokenNotFound(token: String, siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0003"
    override val title: String = s"Company user token $token with siret ${siret.value} not found"
    override val details: String = s"Le lien d'activation ($token) n'est pas valide. Merci de contacter le support"
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
    override val title: String = s"Company already associated to website  ${websiteId.toString}"
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

  final case class TooMuchAuthAttempts(userId: UUID) extends ForbiddenError {
    override val `type`: String = "SC-0010"
    override val title: String = s"Max auth attempt reached for user id : $userId"
    override val details: String =
      "Le nombre maximum de tentative d'authentification a été dépassé, merci de rééssayer un peu plus tard."
  }

  final case class InvalidPassword(login: String) extends UnauthorizedError {
    override val `type`: String = "SC-0011"
    override val title: String = "Invalid password"
    override val details: String =
      s"Mot de passe invalide pour $login"
  }

  final case class UserNotFound(login: String) extends UnauthorizedError {
    override val `type`: String = "SC-0012"
    override val title: String = "User not found"
    override val details: String =
      s"Aucun utilisateur trouvé pour $login"
  }

  final case class DGCCRFUserEmailValidationExpired(login: String) extends ForbiddenError {
    override val `type`: String = "SC-0013"
    override val title: String = "DGCCRF user needs email revalidation"
    override val details: String =
      s"Votre compte DGCCRF a besoin d'être revalidé, un email vous a été envoyé pour réactiver votre compte."
  }

  final case object MalformedBody extends BadRequestError {
    override val `type`: String = "SC-0014"
    override val title: String = "Malformed request body"
    override val details: String = s"Le corps de la requête ne correspond pas à ce qui est attendu par l'API."
  }

  final case class PasswordTokenNotFoundOrInvalid(token: UUID) extends NotFoundError {
    override val `type`: String = "SC-0015"
    override val title: String = s"Token not found / invalid ${token.toString}"
    override val details: String =
      s"Lien invalide ou expiré, merci de recommencer la demande de changement de mot de passe."
  }

  final case class AccountActivationTokenNotFoundOrInvalid(token: String) extends NotFoundError {
    override val `type`: String = "SC-0015"
    override val title: String = s"Account activation token not found / invalid ${token}"
    override val details: String =
      s"Lien invalide ou expiré, merci de recommencer la procédure d'activation du compte."
  }

  final case object EmailAlreadyExist extends ConflictError {
    override val `type`: String = "SC-0016"
    override val title: String = s"Email already exists"
    override val details: String =
      s"L'adresse email existe déjà."
  }

  final case object SamePasswordError extends BadRequestError {
    override val `type`: String = "SC-0016"
    override val title: String = s"New password is equal to old password"
    override val details: String =
      s"Le nouveau mot de passe ne peut pas être le même que l'ancien mot de passe"
  }

  /** Error message is not precice on purpose, to prevent third party to crawl SIRET / CODE from our API
    */
  final case class CompanyActivationSiretOrCodeInvalid(siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0017"
    override val title: String = s"Unable to activate company"
    override val details: String =
      s"Impossible d'activer l'entreprise (siret : ${siret.value}), merci de vérifier que le siret et le code d'activation correspondent bien à ceux indiqués sur le courrier."
  }

  final case class CompanyActivationCodeExpired(siret: SIRET) extends BadRequestError {
    override val `type`: String = "SC-0018"
    override val title: String = s"Unable to activate company, code expired"
    override val details: String =
      s"Impossible d'activer l'entreprise (siret : ${siret.value}) car le code a expiré, merci de contacter le support."
  }

  final case class ActivationCodeAlreadyUsed(email: EmailAddress) extends ConflictError {
    override val `type`: String = "SC-0019"
    override val title: String = s"Unable to activate company, code already used"
    override val details: String =
      s"Compte déjà activé, merci de vous connecter avec l'adresse ${email.value} ( vous pouvez recréer un mot de passe en cliquant sur 'MOT DE PASSE OUBLIÉ' sur la page de connexion.)"
  }

  final case class InvalidEmail(email: String) extends BadRequestError {
    override val `type`: String = "SC-0020"
    override val title: String = "Invalid email"
    override val details: String =
      s"Email ${email} est invalide."
  }

  final case object InvalidEmailProvider extends BadRequestError {
    override val `type`: String = "SC-0021"
    override val title: String = "Invalid email provider"
    override val details: String =
      s"Les adresses email temporaires sont interdites."
  }

  /** Error message is not precice on purpose to prevent third parties for sniffing emails
    */
  final case class EmailOrCodeIncorrect(email: EmailAddress) extends NotFoundError {
    override val `type`: String = "SC-0020"
    override val title: String = s"Email or code incorrect"
    override val details: String =
      s"Impossible de valider l'email ${email}, code ou email incorrect."
  }

  final case class SpammerEmailBlocked(email: EmailAddress) extends NotFoundError {
    override val `type`: String = "SC-0020"
    override val title: String = s"Email blocked, report submission ignored"
    override val details: String =
      s"L'email ${email.value} est bloquée car listée comme spam"
  }

  final case object ReportCreationInvalidBody extends BadRequestError {
    override val `type`: String = "SC-0021"
    override val title: String = s"Report's body does not match specific constraints"
    override val details: String = s"Le signalement est invalide"
  }
}
