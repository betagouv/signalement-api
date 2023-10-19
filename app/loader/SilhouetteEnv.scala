package loader

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.EventBus
import com.mohiva.play.silhouette.api.RequestProvider
import com.mohiva.play.silhouette.api.crypto.CrypterAuthenticatorEncoder
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.crypto.JcaCrypter
import com.mohiva.play.silhouette.crypto.JcaCrypterSettings
import com.mohiva.play.silhouette.crypto.JcaSigner
import com.mohiva.play.silhouette.crypto.JcaSignerSettings
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorService
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorSettings
import com.mohiva.play.silhouette.impl.util.DefaultFingerprintGenerator
import com.mohiva.play.silhouette.impl.util.SecureRandomIDGenerator
import com.typesafe.config.Config
import play.api.Configuration
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import play.api.mvc.Cookie
import play.api.mvc.DefaultCookieHeaderEncoding

import scala.concurrent.ExecutionContext

object SilhouetteEnv {

  implicit val sameSiteReader: ValueReader[Cookie.SameSite] = (config: Config, path: String) =>
    config.getString(path) match {
      case "strict" => Cookie.SameSite.Strict
      case "none"   => Cookie.SameSite.None
      case _        => Cookie.SameSite.Lax
    }

  private val eventBus: EventBus = EventBus()

  def getEnv[E <: Env](
      identityServiceImpl: IdentityService[E#I],
      authenticatorServiceImpl: AuthenticatorService[E#A],
      requestProvidersImpl: Seq[RequestProvider] = Seq()
  )(implicit ec: ExecutionContext): Environment[E] =
    Environment[E](
      identityServiceImpl,
      authenticatorServiceImpl,
      requestProvidersImpl,
      eventBus
    )

  def getCookieAuthenticatorService(
      configuration: Configuration
  )(implicit ec: ExecutionContext): AuthenticatorService[CookieAuthenticator] = {

    val cookieAuthenticatorSettings =
      configuration.underlying.as[CookieAuthenticatorSettings]("silhouette.authenticator.cookie")

    val crypterSettings = configuration.underlying.as[JcaCrypterSettings]("silhouette.authenticator.crypter")
    val crypter         = new JcaCrypter(crypterSettings)

    val signerSettings = configuration.underlying.as[JcaSignerSettings]("silhouette.authenticator.signer")
    val signer         = new JcaSigner(signerSettings)

    val encoder = new CrypterAuthenticatorEncoder(crypter)

    new CookieAuthenticatorService(
      cookieAuthenticatorSettings,
      None,
      signer,
      new DefaultCookieHeaderEncoding(),
      encoder,
      new DefaultFingerprintGenerator(),
      new SecureRandomIDGenerator,
      Clock()
    )

  }

}
