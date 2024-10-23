package orchestrators.proconnect

import cats.implicits.toBifunctorOps
import controllers.error.AppError
import controllers.error.AppError.ProConnectSessionInvalidClaim
import models.proconnect.ProConnectClaim
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import repositories.proconnect.ProConnectSession
import repositories.proconnect.ProConnectSessionRepositoryInterface
import utils.Logs.RichLogger
import cats.instances.either._
import java.util.Base64
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ProConnectOrchestrator(
    proConnectClient: ProConnectClient,
    proConnectSessionRepository: ProConnectSessionRepositoryInterface
)(implicit
    val executionContext: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

  def saveState(state: String): Future[ProConnectSession] =
    proConnectSessionRepository.create(ProConnectSession(state))

  def login(code: String, state: String) =
    for {
      maybeStoredState <- proConnectSessionRepository.find(state)
      _ <- maybeStoredState match {
        case Some(_) => Future.successful(())
        case None =>
          logger.errorWithTitle(
            "csrf_state_mismatch",
            s"State ${state} not found, is this the result of a csrf attack ?"
          )
          Future.failed(AppError.ProConnectSessionNotFound(state))
      }
      token  <- proConnectClient.getToken(code)
      jwtRaw <- proConnectClient.userInfo(token)
      _ = println(s"------------------ jwtRaw = ${decodeClaim(jwtRaw)} ------------------")
    } yield token.id_token

  private def base64Decode(input: String): String = {
    val decoder = Base64.getUrlDecoder
    // Fix missing padding (Base64 strings should have length multiple of 4)
    val paddedInput = input + ("=" * ((4 - input.length % 4) % 4))
    new String(decoder.decode(paddedInput))
  }

  // Function to decode JWT and return the header and payload as JSON (using Circe)
  private def decodeClaim(token: String): Either[ProConnectSessionInvalidClaim, ProConnectClaim] = {
    // Split the token into parts (header, payload, signature)
    val parts = token.split("\\.")
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid JWT token")
    }

    // Decode header and payload
    val headerDecoded  = base64Decode(parts(0))
    val payloadDecoded = base64Decode(parts(1))

    // Parse as JSON
    val headerJson  = Json.parse(headerDecoded)
    val payloadJson = Json.parse(payloadDecoded)

    payloadJson.validate[ProConnectClaim].asEither.leftMap { err =>
      ProConnectSessionInvalidClaim(Json.stringify(JsError.toJson(err)))
    }.liftTo[Future]

  }

}
