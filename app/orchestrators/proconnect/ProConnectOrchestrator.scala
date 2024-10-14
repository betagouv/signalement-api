package orchestrators.proconnect

import cats.implicits.catsSyntaxOption
import cats.syntax.either._
import controllers.error.AppError
import controllers.error.AppError.ProConnectSessionInvalidJwt
import controllers.error.AppError.UserNotAllowedToAccessSignalConso
import models.UserRole.DGCCRF
import models.proconnect.ProConnectClaim
import models.proconnect.ProConnectNonce
import orchestrators.UserOrchestratorInterface
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import repositories.proconnect.ProConnectSession
import repositories.proconnect.ProConnectSessionRepositoryInterface
import utils.Logs.RichLogger

import java.util.Base64
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ProConnectOrchestrator(
    proConnectClient: ProConnectClient,
    proConnectSessionRepository: ProConnectSessionRepositoryInterface,
    userOrchestrator: UserOrchestratorInterface,
    allowedProvidersIds: List[String]
)(implicit
    val executionContext: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

  def saveState(state: String, nonce: String): Future[ProConnectSession] =
    proConnectSessionRepository.create(ProConnectSession(state, nonce))

  def login(code: String, state: String) =
    for {
      session <- validateState(state)
      token   <- proConnectClient.getToken(code)
      _       <- validateNonce(session, token.id_token)
      jwtRaw  <- proConnectClient.userInfo(token)
      claim   <- decodeClaim(jwtRaw).liftTo[Future]
      _    <- allowedProvidersIds.find(_ == claim.idp_id).liftTo[Future](UserNotAllowedToAccessSignalConso(claim.email))
      user <- userOrchestrator.getProConnectUser(claim, DGCCRF)
    } yield (token.id_token, user)

  private def validateState(state: String) = for {
    maybeStoredState <- proConnectSessionRepository.find(state)
    proConnectSession <- maybeStoredState match {
      case Some(session) => Future.successful(session)
      case None =>
        logger.errorWithTitle(
          "csrf_state_mismatch",
          s"State ${state} not found, is this the result of a csrf attack ?"
        )
        Future.failed(AppError.ProConnectSessionNotFound(state))
    }
  } yield proConnectSession

  private def validateNonce(session: ProConnectSession, idToken: String) =
    decodeNonce(idToken) match {
      case Right(tokenNonce) if tokenNonce.nonce.toLowerCase().trim == session.nonce.toLowerCase().trim => Future.unit
      case _ =>
        logger.errorWithTitle(
          "csrf_nonce_mismatch_or_not_found",
          s"Nonce ${session.nonce} not found in idtoken $idToken"
        )
        Future.failed(AppError.ProConnectSessionNotFound(session.nonce))
    }

  private def base64Decode(input: String): String = {
    val decoder = Base64.getUrlDecoder
    // Fix missing padding (Base64 strings should have length multiple of 4)
    val paddedInput = input + ("=" * ((4 - input.length % 4) % 4))
    new String(decoder.decode(paddedInput))
  }

  private def decodeClaim(token: String): Either[ProConnectSessionInvalidJwt, ProConnectClaim] = {
    val payloadJson: JsValue = decodeJwt(token)
    payloadJson.validate[ProConnectClaim].asEither.leftMap { err =>
      ProConnectSessionInvalidJwt(Json.stringify(JsError.toJson(err)))
    }
  }

  private def decodeNonce(token: String): Either[ProConnectSessionInvalidJwt, ProConnectNonce] = {
    val payloadJson: JsValue = decodeJwt(token)
    payloadJson.validate[ProConnectNonce].asEither.leftMap { err =>
      ProConnectSessionInvalidJwt(Json.stringify(JsError.toJson(err)))
    }
  }

  private def decodeJwt(token: String) = {
    // Split the token into parts (header, payload, signature)
    val parts = token.split("\\.")
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid JWT token")
    }
    val payloadDecoded = base64Decode(parts(1))

    val payloadJson = Json.parse(payloadDecoded)
    payloadJson
  }

  def endSessionUrl(id_token: String, state: String) =
    for {
      _ <- validateState(state)
    } yield proConnectClient.endSessionUrl(id_token, state)

}
