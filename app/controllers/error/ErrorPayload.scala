package controllers.error

import play.api.libs.json.Format
import play.api.libs.json.Json

final case class ErrorPayload(`type`: String, title: String, details: String)

object ErrorPayload {
  def apply(error: AppError): ErrorPayload = ErrorPayload(error.`type`, error.title, error.details)

  val AuthenticationErrorPayload = ErrorPayload(
    "SC-AUTH",
    "Cannot authenticate user",
    """Impossible de vous authentifier,
      | merci de vérifier que votre mot de passe ou identifiant est correct.
      | Si vous avez oublié votre mot de passe, cliquez sur "MOT DE PASSE OUBLIÉ" pour le récupérer.""".stripMargin
  )

  implicit val ErrorPayloadFormat: Format[ErrorPayload] = Json.format[ErrorPayload]
}
