package models.proconnect

import play.api.libs.json.Json
import play.api.libs.json.Reads

case class ProConnectNonce(nonce: String)

object ProConnectNonce {

  implicit val proConnectNonceReads: Reads[ProConnectNonce] = Json.reads[ProConnectNonce]

}
