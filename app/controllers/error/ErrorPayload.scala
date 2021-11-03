package controllers.error

import play.api.libs.json.Format
import play.api.libs.json.Json

final case class ErrorPayload(`type`: String, title: String, details: String)

object ErrorPayload {
  def apply(error: AppError): ErrorPayload = ErrorPayload(error.`type`, error.title, error.details)

  implicit val ErrorPayloadFormat: Format[ErrorPayload] = Json.format[ErrorPayload]
}
