package controllers.error

import models.report.Report
import models.report.reportfile.ReportFileId
import models.website.WebsiteId
import utils.EmailAddress
import utils.SIRET

import java.util.UUID
import scala.util.control.NoStackTrace

sealed trait AppError extends Throwable with Product with Serializable with NoStackTrace {
  val `type`: String
  val title: String
  val details: String
  lazy val messageInLogs = details
  val titleForLogs: String
}

sealed trait UnauthorizedError extends AppError
sealed trait NotFoundError extends AppError
sealed trait BadRequestError extends AppError
sealed trait MalformedApiBadRequestError extends AppError
sealed trait ForbiddenError extends AppError
sealed trait ConflictError extends AppError
sealed trait InternalAppError extends AppError
sealed trait PreconditionError extends AppError

object AppError {

  final case class ServerError(message: String, cause: Option[Throwable] = None) extends InternalAppError {
    override val `type`: String = "SC-0001"
    override val title: String = message
    override val details: String = "Une erreur inattendue s'est produite."
    override val titleForLogs: String = "server_error"
  }

  final case class DGCCRFActivationTokenNotFound(token: String) extends NotFoundError {
    override val `type`: String = "SC-0002"
    override val title: String = s"DGCCRF user token $token not found"
    override val details: String = s"Le lien d'activation n'est pas valide ($token). Merci de contacter le support"
    override val titleForLogs: String = "dgccrf_activation_token_not_found"
  }

  final case class CompanyActivationTokenNotFound(token: String, siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0003"
    override val title: String = s"Company user token $token with siret ${siret.value} not found"
    override val details: String = s"Le lien d'activation ($token) n'est pas valide. Merci de contacter le support"
    override val titleForLogs: String = "company_activation_token_not_found"
  }

  final case class CompanySiretNotFound(siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0004"
    override val title: String = s"Company siret not found"
    override val details: String = s"Le SIRET ${siret.value} ne correspond à aucune entreprise connue"
    override val titleForLogs: String = "company_siret_not_found"
  }

  final case class WebsiteNotFound(websiteId: WebsiteId) extends NotFoundError {
    override val `type`: String = "SC-0005"
    override val title: String = s"Website ${websiteId.value.toString} not found"
    override val details: String = "L'association site internet n'existe pas."
    override val titleForLogs: String = "website_not_found"
  }

  final case class WebsiteHostIsAlreadyIdentified(
      host: String,
      companyId: Option[UUID] = None,
      country: Option[String] = None
  ) extends ConflictError {
    override val `type`: String = "SC-0006"
    override val title: String = s"Website ${host} already associated to a country or company"
    override val details: String =
      s"Le site internet ${host} est déjà associé à un autre pays ou une entreprise"
    override val titleForLogs: String = "website_host_already_identified"
  }

  final case class MalformedHost(host: String) extends BadRequestError {
    override val `type`: String = "SC-0007"
    override val title: String = "Malformed host"
    override val details: String =
      s"Le site $host doit être un site valide."
    override val titleForLogs: String = "malformed_website_host"
  }

  final case class InvalidDGCCRFOrAdminEmail(email: EmailAddress) extends ForbiddenError {
    override val `type`: String = "SC-0008"
    override val title: String = "Invalid email for this type of user"
    override val details: String =
      s"Email ${email.value} invalide pour ce type d'utilisateur"
    override val titleForLogs: String = "invalid_email_for_admin_or_dgccrf"
  }

  final case object UserAccountEmailAlreadyExist extends ConflictError {
    override val `type`: String = "SC-0009"
    override val title: String = "User already exist"
    override val details: String =
      s"Ce compte existe déjà. Merci de demander à l'utilisateur de regénérer son mot de passe pour se connecter"
    override val titleForLogs: String = "user_already_exists"
  }

  final case class TooMuchAuthAttempts(login: String) extends ForbiddenError {
    override val `type`: String = "SC-0010"
    override val title: String = s"Max auth attempt reached for login : $login"
    override val details: String =
      "Le nombre maximum de tentatives d'authentification a été dépassé, merci de rééssayer un peu plus tard."
    override val titleForLogs: String = "max_auth_attempts_reached"
  }

  final case class InvalidPassword(login: String) extends UnauthorizedError {
    override val `type`: String = "SC-0011"
    override val title: String = "Invalid password"
    override val details: String =
      s"Mot de passe invalide pour $login"
    override val titleForLogs: String = "invalid_password"
  }

  final case class UserNotFound(login: String) extends UnauthorizedError {
    override val `type`: String = "SC-0012"
    override val title: String = "Cannot perform action on user"
    override val details: String =
      s"Action non autorisée pour $login"
    override val titleForLogs: String = "user_not_found"
  }

  final case class DGCCRFUserEmailValidationExpired(login: String) extends ForbiddenError {
    override val `type`: String = "SC-0013"
    override val title: String = "DGCCRF user needs email revalidation"
    override val details: String =
      s"Votre compte DGCCRF a besoin d'être revalidé, un email vous a été envoyé pour réactiver votre compte."
    override val titleForLogs: String = "dgccrf_user_needs_revalidation"
  }

  final case object MalformedBody extends MalformedApiBadRequestError {
    override val `type`: String = "SC-0014"
    override val title: String = "Malformed request body"
    override val details: String = s"Le corps de la requête ne correspond pas à ce qui est attendu par l'API."
    override val titleForLogs: String = "malformed_request_body"
  }

  final case class PasswordTokenNotFoundOrInvalid(token: UUID) extends NotFoundError {
    override val `type`: String = "SC-0015-01"
    override val title: String = s"Token not found / invalid ${token.toString}"
    override val details: String =
      s"Lien invalide ou expiré, merci de recommencer la demande de changement de mot de passe."
    override val titleForLogs: String = "password_token_not_found_or_invalid"
  }

  final case class AccountActivationTokenNotFoundOrInvalid(token: String) extends NotFoundError {
    override val `type`: String = "SC-0015-02"
    override val title: String =
      s"Account activation token not found / invalid for token ${token}"
    override val details: String =
      s"Lien invalide ou expiré, merci de contacter le support en donnant votre adresse email."
    override val titleForLogs: String = "account_activation_token_not_found_or_invalid"
  }

  final case class ProAccountActivationTokenNotFoundOrInvalid(token: String, SIRET: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0015-03"
    override val title: String = s"Account activation token not found / invalid ${token} and SIRET ${SIRET.value}"
    override val details: String =
      s"Lien invalide ou expiré, merci de contacter le support en donnant votre adresse email et numéro de SIRET."
    override val titleForLogs: String = "account_activation_token_not_found_or_invalid"
  }

  final case object EmailAlreadyExist extends ConflictError {
    override val `type`: String = "SC-0016-01"
    override val title: String = s"Email already exists"
    override val details: String =
      s"L'adresse email existe déjà."
    override val titleForLogs: String = "email_already_exists"
  }

  final case object SamePasswordError extends BadRequestError {
    override val `type`: String = "SC-0016-02"
    override val title: String = s"New password is equal to old password"
    override val details: String =
      s"Le nouveau mot de passe ne peut pas être le même que l'ancien mot de passe"
    override val titleForLogs: String = "same_password"
  }

  /** Error message is not precice on purpose, to prevent third party to crawl SIRET / CODE from our API
    */
  final case class CompanyActivationSiretOrCodeInvalid(siret: SIRET) extends NotFoundError {
    override val `type`: String = "SC-0017"
    override val title: String = s"Unable to activate company"
    override val details: String =
      s"Impossible de créer le compte. Merci de vérifier que le numéro SIRET et le code d'activation correspondent bien à ceux indiqués dans le courrier."
    override val titleForLogs: String = "company_activation_invalid_inputs"
  }

  final case class CompanyActivationCodeExpired(siret: SIRET) extends BadRequestError {
    override val `type`: String = "SC-0018"
    override val title: String = s"Unable to activate company, code expired"
    override val details: String =
      s"Impossible de créer le compte car ce code d'activation a expiré. Si vous avez un courrier de SignalConso avec un code d'activation plus récent, essayez-le. Sinon, merci de contacter le support."
    override val titleForLogs: String = "company_activation_code_expired"
  }

  final case class ActivationCodeAlreadyUsed() extends ConflictError {
    override val `type`: String = "SC-0019"
    override val title: String = s"Unable to activate company, code already used"
    override val details: String =
      s"Ce code a déjà été utilisé et un compte a été créé. Merci de vous connecter directement."
    override val titleForLogs: String = "company_activation_code_already_used"
  }

  final case class InvalidEmail(email: String) extends BadRequestError {
    override val `type`: String = "SC-0020-01"
    override val title: String = "Invalid email"
    override val details: String =
      s"Email ${email} est invalide."
    override val titleForLogs: String = "invalid_email"
  }

  final case object InvalidEmailProvider extends BadRequestError {
    override val `type`: String = "SC-0020-02"
    override val title: String = "Invalid email provider"
    override val details: String =
      s"Les adresses email temporaires sont interdites."
    override val titleForLogs: String = "invalid_email_provider"
  }

  /** Error message is not precice on purpose to prevent third parties for sniffing emails
    */
  final case class EmailOrCodeIncorrect(email: EmailAddress) extends NotFoundError {
    override val `type`: String = "SC-0020-03"
    override val title: String = s"Email or code incorrect"
    override val details: String =
      s"Impossible de valider l'email ${email}, code ou email incorrect."
    override val titleForLogs: String = "email_or_code_incorrect"
  }

  final case class SpammerEmailBlocked(email: EmailAddress) extends NotFoundError {
    override val `type`: String = "SC-0020-04"
    override val title: String = s"Email blocked, report submission ignored"
    override val details: String =
      s"L'email ${email.value} est bloquée car listée comme spam"
    override val titleForLogs: String = "spammer_email_blocked"
  }

  final case object ReportCreationInvalidBody extends MalformedApiBadRequestError {
    override val `type`: String = "SC-0021"
    override val title: String = s"Report's body does not match specific constraints"
    override val details: String = s"Le signalement est invalide"
    override val titleForLogs: String = "invalid_report_body"

  }

  final case class InvalidReportTagBody(name: String) extends MalformedApiBadRequestError {
    override val `type`: String = "SC-0022"
    override val title: String = s"Unknown report tag $name"
    override val details: String = s"Le tag $name est invalide. Merci de fournir une valeur correcte."
    override val titleForLogs: String = "invalid_report_tag"

  }

  final case class ExternalReportsMaxPageSizeExceeded(maxSize: Int) extends BadRequestError {
    override val `type`: String = "SC-0024"
    override val title: String = s"Max page size reached "
    override val details: String =
      s"Le nombre d'entrée par page demandé est trop élevé. Il doit être inférieur ou égal à $maxSize"
    override val titleForLogs: String = "external_reports_max_size_exceeded"

  }

  final case class DuplicateReportCreation(reportList: List[Report]) extends ConflictError {
    override val `type`: String = "SC-0025"
    override val title: String = s"Same report has already been created with id ${reportList.map(_.id).mkString(",")}"
    override val details: String =
      s"Il existe un ou plusieurs signalements similaire"
    override val titleForLogs: String = "duplicate_report_creation"

  }

  final case object MalformedQueryParams extends MalformedApiBadRequestError {
    override val `type`: String = "SC-0026"
    override val title: String = "Malformed request query params"
    override val details: String = s"Le paramètres de la requête ne correspondent pas à ce qui est attendu par l'API."
    override val titleForLogs: String = "malformed_query"

  }

  final case class AttachmentNotReady(reportFileId: ReportFileId) extends ConflictError {
    override val `type`: String = "SC-0027"
    override val title: String = "Attachement not available"
    override val details: String =
      s"Le fichier n'est pas encore disponible au téléchargement, veuillez réessayer plus tard."
    override lazy val messageInLogs = s"Attachement not ready for download (${reportFileId.value})"
    override val titleForLogs: String = "attachment_not_ready_for_download"

  }

  final case class AttachmentNotFound(reportFileId: ReportFileId, reportFileName: String) extends NotFoundError {
    override val `type`: String = "SC-0028"
    override val title: String = "Cannot download attachment"
    override val details: String =
      s"Impossible de récupérer le fichier [id = ${reportFileId.value.toString}, nom = $reportFileName]"
    override val titleForLogs: String = "attachment_not_found"
  }

  final case class BucketFileNotFound(bucketName: String, fileName: String) extends NotFoundError {
    override val `type`: String = "SC-0029"
    override val title: String = "Cannot download file from S3"
    override val details: String =
      s"Impossible de récupérer le fichier $fileName sur le bucket $bucketName"
    override val titleForLogs: String = "file_not_found_on_bucklet"
  }

  final case class CannotReviewReportResponse(reportId: UUID) extends ForbiddenError {
    override val `type`: String = "SC-0030"
    override val title: String = "Cannot review response for report"
    override val details: String =
      s"Impossible de donner un avis sur la réponse donnée au signalement ${reportId.toString}"
    override val titleForLogs: String = "cannot_review_report_response"
  }

  final case class MalformedId(id: String) extends MalformedApiBadRequestError {
    override val `type`: String = "SC-0031"
    override val title: String = "Malformed id"
    override val details: String =
      s"Malformed id : $id"
    override val titleForLogs: String = "cannot_review_report_response"

  }

  final case object ReviewDoesNotExists extends NotFoundError {
    override val `type`: String = "SC-0032"
    override val title: String = "Review does not exist for the report response."
    override val details: String =
      s"Un avis existe déjà pour la réponse de l'entreprise."
    override val titleForLogs: String = "review_already_exists"

  }

  final case class ReportNotFound(reportId: UUID) extends NotFoundError {
    override val `type`: String = "SC-0033"
    override val title: String = s"Report with id ${reportId.toString} not found"
    override val details: String =
      s"Signalement avec id ${reportId.toString} introuvable"
    override val titleForLogs: String = "report_not_found"
  }

  final case class MalformedSIRET(InvalidSIRET: String) extends MalformedApiBadRequestError {
    override val `type`: String = "SC-0034"
    override val title: String = "Malformed SIRET"
    override val details: String =
      s"Malformed SIRET : $InvalidSIRET"
    override val titleForLogs: String = "malformed_siret"
  }

  final case class CompanyNotFound(companyId: UUID) extends NotFoundError {
    override val `type`: String = "SC-0035"
    override val title: String = s"Company with id ${companyId.toString} not found"
    override val details: String =
      s"Entreprise avec id ${companyId.toString} introuvable"
    override val titleForLogs: String = "company_not_found"

  }

  final case object CantPerformAction extends ForbiddenError {
    override val `type`: String = "SC-0036"
    override val title: String = s"Access forbidden"
    override val details: String =
      s"L'action demandée n'est pas autorisée."
    override val titleForLogs: String = "cant_perform_action"
  }

  final case class InvalidFileExtension(currentExtension: String, validExtensions: Seq[String])
      extends BadRequestError {
    override val `type`: String = "SC-0037"
    override val title: String = s"Invalid file extension"
    override val details: String =
      s"Impossible de charger un fichier avec l'extension '.$currentExtension', extensions valides : ${validExtensions
          .mkString("'", "' , '", "'")}"
    override val titleForLogs: String = "invalid_file_extension"
  }

  final case class MalformedFileKey(key: String) extends BadRequestError {
    override val `type`: String = "SC-0038"
    override val title: String = "Malformed file key"
    override val details: String =
      s"Cannot find file with key $key"
    override val titleForLogs: String = "malformed_file_key"
  }

  final case class WebsiteNotIdentified(host: String) extends BadRequestError {
    override val `type`: String = "SC-0039"
    override val title: String = s"Website must be associated to identify or update investigation"
    override val details: String =
      s"Le site $host doit être associé à une entreprise ou un pays pour l'identifier ou modifier l'enquête"
    override val titleForLogs: String = "website_not_identified"

  }

  final case class CannotDeleteWebsite(host: String) extends BadRequestError {
    override val `type`: String = "SC-0040"
    override val title: String = s"Website must not be under investigation or identified"
    override val details: String =
      s"Impossible de supprimer le site. Vérifiez que le site ne soit pas identifié ou qu'il ne fasse pas l'objet d'une enquête / affectation"
    override val titleForLogs: String = "website_cannot_delete"
  }

  final case object CannotReportPublicAdministration extends BadRequestError {
    override val `type`: String = "SC-0041"
    override val title: String = s"Cannot report public administration"
    override val details: String =
      s"Impossible de signaler une administration publique"
    override val titleForLogs: String = "cannot_report_public_administration"
  }

  final case class MalformedValue(value: String, expectedValidType: String) extends MalformedApiBadRequestError {
    override val `type`: String = "SC-0042"
    override val title: String = s"Malformed value, $value is not a valid value, expecting valid $expectedValidType"
    override val details: String =
      s"La valeur $value ne correspond pas à ce qui est attendu par l'API. Merci de renseigner une valeur valide pour $expectedValidType"
    override val titleForLogs: String = "malformed_value"

  }

  final case class DeletedAccount(login: String) extends BadRequestError {
    override val `type`: String = "SC-0043"
    override val title: String = "Deleted account"
    override val details: String =
      "Votre compte a été supprimé, veuillez envoyer un mail à support@signal.conso.gouv.fr"
    override val titleForLogs: String = "deleted_account"
  }

  final case class TooMuchCompanyActivationAttempts(siret: SIRET) extends ForbiddenError {
    override val `type`: String = "SC-0044"
    override val title: String = s"Max activation attempts reached for siret : ${siret.value}"
    override val details: String =
      "Le nombre maximum de tentatives a été dépassé, merci de rééssayer un peu plus tard."
    override val titleForLogs: String = "max_activation_attempts_reached"
  }

  final case class FileTooLarge(maxSize: Int, filename: String) extends BadRequestError {
    override val `type`: String = "SC-0045"
    override val title: String = "File too large"
    override val details: String = s"File $filename is too large, it must not exceed $maxSize MB"
    override val titleForLogs: String = "file_too_large"
  }

  final case class TooManyAttachments(max: Int, current: Int) extends BadRequestError {
    override val `type`: String = "SC-0046"
    override val title: String = "Too many attachments"
    override val details: String = s"Reports have $current attachments while the max allowed is $max"
    override val titleForLogs: String = "too_many_attachments"
  }

  final case object PasswordNotComplexEnoughError extends BadRequestError {
    override val `type`: String = "SC-0047"
    override val title: String = "Password not complex enough"
    override val details: String = s"Le mot de passe choisi ne respecte pas les critères demandés"
    override val titleForLogs: String = "pwd_not_complex_enough"
  }
}
