package authentication

import authentication.CookieAuthenticator.CookieAuthenticatorSettings
import controllers.error.AppError.BrokenAuthError
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
    settings: CookieAuthenticatorSettings,
    userRepository: UserRepositoryInterface
)(implicit ec: ExecutionContext)
    extends Authenticator[User] {

  private def create(
      userEmail: EmailAddress,
      impersonator: Option[EmailAddress]
  ): CookieInfos = {
    val now = OffsetDateTime.now()
    CookieInfos(
      id = UUID.randomUUID().toString,
      userEmail = userEmail,
      impersonator = impersonator,
      lastUsedDateTime = now,
      expirationDateTime = now.plus(settings.authenticatorExpiry.toMillis, ChronoUnit.MILLIS),
      idleTimeout = settings.authenticatorIdleTimeout,
      cookieMaxAge = settings.cookieMaxAge
    )
  }

  private def unserialize(str: String): Either[BrokenAuthError, CookieInfos] =
    for {
      data          <- signer.extract(str)
      decryptedData <- crypter.decrypt(data)
      cookiesInfos <- Json
        .parse(decryptedData)
        .validate[CookieInfos]
        .asEither
        .left
        .map(_ => BrokenAuthError("Error while extracting data from cookie"))
    } yield cookiesInfos

  def extract[B](request: Request[B]): Either[BrokenAuthError, CookieInfos] =
    request.cookies.get(settings.cookieName) match {
      case Some(cookie) => unserialize(cookie.value)
      case None         => Left(BrokenAuthError("Cookie not found"))
    }

  def authenticate[B](request: Request[B]): Future[Either[BrokenAuthError, Option[User]]] =
    extract(request) match {
      case Right(cookieInfos) =>
        userRepository
          .findByEmail(cookieInfos.userEmail.value)
          .map(_.map(_.copy(impersonator = cookieInfos.impersonator)))
          .map(Right(_))
      case Left(authError) => Future.successful(Left(authError))
    }

  private def serialize(cookieInfos: CookieInfos): Either[BrokenAuthError, String] = for {
    crypted <- crypter.encrypt(Json.toJson(cookieInfos).toString())
  } yield signer.sign(crypted)

  def initSignalConsoCookie(
      userEmail: EmailAddress,
      impersonator: Option[EmailAddress]
  ): Either[BrokenAuthError, Cookie] = {
    val cookieInfos = create(userEmail, impersonator)
    init(cookieInfos)
  }

  private def init(cookieInfos: CookieInfos): Either[BrokenAuthError, Cookie] =
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
      useFingerprinting: Boolean = false,
      cookieMaxAge: Option[FiniteDuration],
      authenticatorIdleTimeout: Option[FiniteDuration] = None,
      authenticatorExpiry: FiniteDuration = 12.hours
  )
}
