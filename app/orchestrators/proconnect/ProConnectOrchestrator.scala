package orchestrators.proconnect

import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import java.util.Base64
import scala.concurrent.ExecutionContext

class ProConnectOrchestrator(
    proConnectClient: ProConnectClient
)(implicit
    val executionContext: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

  def login(code: String, state: String) = {
    println(s"------------------ (code,state,id_token) = ${(code, state)} ------------------")
    for {
      token <- proConnectClient.getToken(code)
      jwtRaw <- proConnectClient.userInfo(token)
      _ = println(s"------------------ jwtRaw = ${decodeJwt(jwtRaw)} ------------------")
    } yield token.id_token
  }

  def base64Decode(input: String): String = {
    val decoder = Base64.getUrlDecoder
    // Fix missing padding (Base64 strings should have length multiple of 4)
    val paddedInput = input + ("=" * ((4 - input.length % 4) % 4))
    new String(decoder.decode(paddedInput))
  }

  // Function to decode JWT and return the header and payload as JSON (using Circe)
  def decodeJwt(token: String): (JsValue, JsValue) = {
    // Split the token into parts (header, payload, signature)
    val parts = token.split("\\.")
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid JWT token")
    }

    // Decode header and payload
    val headerDecoded = base64Decode(parts(0))
    val payloadDecoded = base64Decode(parts(1))

    // Parse as JSON
    val headerJson = Json.parse(headerDecoded)
    val payloadJson = Json.parse(payloadDecoded)

    (headerJson, payloadJson)
  }


}
