package authentication

import authentication.CookieAuthenticator.CookieAuthenticatorSettings
import controllers.error.AppError.AuthError
import models.User
import play.api.libs.json.Json
import play.api.mvc._
import repositories.user.UserRepositoryInterface
import utils.EmailAddress

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CookieAuthenticator(
    signer: JcaSigner,
    crypter: JcaCrypter,
    fingerprintGenerator: FingerprintGenerator,
    settings: CookieAuthenticatorSettings,
    userRepository: UserRepositoryInterface
)(implicit ec: ExecutionContext)
    extends Authenticator[User] {

  private def unserialize(str: String): Either[AuthError, CookieInfos] =
    for {
      data          <- signer.extract(str)
      decryptedData <- crypter.decrypt(data)
      cookiesInfos <- Json
        .parse(decryptedData)
        .validate[CookieInfos]
        .asEither
        .left
        .map(_ => AuthError("Error while extracting data from cookie"))
    } yield cookiesInfos

  def extract[B](request: Request[B]): Either[AuthError, CookieInfos] = {
    val maybeFingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate(request)) else None
    val maybeCookiesInfos = request.cookies.get(settings.cookieName) match {
      case Some(cookie) => unserialize(cookie.value)
      case None         => Left(AuthError("Cookie not found"))
    }

    maybeCookiesInfos match {
      case Right(a) if maybeFingerprint.isDefined && a.fingerprint != maybeFingerprint =>
        Left(AuthError("Fingerprint does not match"))
      case v => v
    }
  }

  def authenticate[B](request: Request[B]): Future[Either[AuthError, Option[User]]] =
    extract(request) match {
      case Right(cookieInfos) =>
        userRepository
          .findByEmail(cookieInfos.userEmail.value)
          .map(_.map(_.copy(impersonator = cookieInfos.impersonator)))
          .map(Right(_))
      case Left(authError) => Future.successful(Left(authError))
    }

  private def serialize(cookieInfos: CookieInfos): Either[AuthError, String] = for {
    crypted <- crypter.encrypt(Json.toJson(cookieInfos).toString())
  } yield signer.sign(crypted)

  private def create(userEmail: EmailAddress, impersonator: Option[EmailAddress] = None)(implicit
      request: RequestHeader
  ): CookieInfos = {
    val now = OffsetDateTime.now()
    CookieInfos(
      id = UUID.randomUUID().toString,
      userEmail = userEmail,
      impersonator = impersonator,
      lastUsedDateTime = now,
      expirationDateTime = now.plus(settings.authenticatorExpiry.toMillis, ChronoUnit.MILLIS),
      idleTimeout = settings.authenticatorIdleTimeout,
      cookieMaxAge = settings.cookieMaxAge,
      fingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate(request)) else None
    )
  }

  def init(userEmail: EmailAddress)(implicit request: RequestHeader): Either[AuthError, Cookie] = {
    val cookieInfos = create(userEmail)
    serialize(cookieInfos).map { value =>
      Cookie(
        name = settings.cookieName,
        value = value,
        // The maxAge` must be used from the authenticator, because it might be changed by the user
        // to implement "Remember Me" functionality
        maxAge = cookieInfos.cookieMaxAge.map(_.toSeconds.toInt),
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie,
        sameSite = settings.sameSite
      )
    }
  }

  def initImpersonated(userEmail: EmailAddress, impersonator: EmailAddress)(implicit
      request: RequestHeader
  ): Either[AuthError, Cookie] = {
    val cookieInfos = create(userEmail, Some(impersonator))
    serialize(cookieInfos).map { value =>
      Cookie(
        name = settings.cookieName,
        value = value,
        // The maxAge` must be used from the authenticator, because it might be changed by the user
        // to implement "Remember Me" functionality
        maxAge = cookieInfos.cookieMaxAge.map(_.toSeconds.toInt),
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie,
        sameSite = settings.sameSite
      )
    }
  }

  def embed(cookie: Cookie, result: Result): Result =
    result.withCookies(cookie)

  def discard(result: Result): Result =
    result.discardingCookies(
      DiscardingCookie(
        name = settings.cookieName,
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        sameSite = settings.sameSite
      )
    )

}

object CookieAuthenticator {
  case class CookieAuthenticatorSettings(
      cookieName: String,
      cookiePath: String = "/",
      cookieDomain: Option[String],
      secureCookie: Boolean,
      httpOnlyCookie: Boolean = true,
      sameSite: Option[Cookie.SameSite],
      useFingerprinting: Boolean = true,
      cookieMaxAge: Option[FiniteDuration],
      authenticatorIdleTimeout: Option[FiniteDuration] = None,
      authenticatorExpiry: FiniteDuration = 12.hours
  )
}
