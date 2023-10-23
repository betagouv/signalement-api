package models.gs1

import play.api.libs.json.JsValue
import play.api.libs.json.Reads

case class OAuthAccessToken(token: String) extends AnyVal

object OAuthAccessToken {
  implicit val readsFromGS1API: Reads[OAuthAccessToken] = (json: JsValue) =>
    (json \ "access_token").validate[String].map(OAuthAccessToken.apply)
}
