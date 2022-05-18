package utils.silhouette

import com.google.inject.name.Named
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler
import com.mohiva.play.silhouette.api.crypto._
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services._
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.EventBus
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.SilhouetteProvider
import com.mohiva.play.silhouette.crypto.JcaCrypter
import com.mohiva.play.silhouette.crypto.JcaCrypterSettings
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.util._
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import models.User
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import utils.ErrorHandler
import net.ceedubs.ficus.readers.EnumerationReader._
import repositories.user.UserRepositoryInterface
import utils.silhouette.api.APIKey
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.api.APIKeyRequestProvider
import utils.silhouette.api.ApiKeyService
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.PasswordInfoDAO
import utils.silhouette.auth.UserService

/** The Guice module which wires all Silhouette dependencies.
  */
class SilhouetteModule extends AbstractModule with ScalaModule {

  /** Configures the module.
    */
  override def configure(): Unit = {
    bind[Silhouette[AuthEnv]].to[SilhouetteProvider[AuthEnv]]
    bind[Silhouette[APIKeyEnv]].to[SilhouetteProvider[APIKeyEnv]]
    bind[SecuredErrorHandler].to[ErrorHandler]
    bind[UnsecuredErrorHandler].to[ErrorHandler]
    bind[IdentityService[User]].to[UserService]
    bind[IdentityService[APIKey]].to[ApiKeyService]

    bind[IDGenerator].toInstance(new SecureRandomIDGenerator)
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
    bind[Clock].toInstance(Clock())
  }

  /** Provides the Silhouette Auth environment.
    *
    * @param userService
    *   The user service implementation.
    * @param authenticatorService
    *   The authentication service implementation.
    * @param eventBus
    *   The event bus instance.
    * @return
    *   The Silhouette environment.
    */
  @Provides
  def provideAuthEnvironment(
      userService: UserService,
      authenticatorService: AuthenticatorService[JWTAuthenticator],
      eventBus: EventBus
  ): Environment[AuthEnv] =
    Environment[AuthEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )

  /** Provides the Silhouette Api environment.
    *
    * @param apiKeyService
    *   The api key service implementation.
    * @param authenticatorService
    *   The authentication service implementation.
    * @param eventBus
    *   The event bus instance.
    * @return
    *   The Silhouette environment.
    */
  @Provides
  def provideApiEnvironment(
      apiKeyService: ApiKeyService,
      authenticatorService: AuthenticatorService[DummyAuthenticator],
      apiKeyRequestProvider: APIKeyRequestProvider,
      eventBus: EventBus
  ): Environment[APIKeyEnv] =
    Environment[APIKeyEnv](
      apiKeyService,
      authenticatorService,
      Seq(apiKeyRequestProvider),
      eventBus
    )

  /** Provides the crypter for the authenticator.
    *
    * @param configuration
    *   The Play configuration.
    * @return
    *   The crypter for the authenticator.
    */
  @Provides
  @Named("authenticator-crypter")
  def provideAuthenticatorCrypter(configuration: Configuration): Crypter = {
    val config = configuration.underlying.as[JcaCrypterSettings]("silhouette.authenticator.crypter")
    new JcaCrypter(config)
  }

  /** Provides the auth info repository.
    *
    * @param passwordInfoDAO
    *   The implementation of the delegable password auth info DAO.
    * @return
    *   The auth info repository instance.
    */
  @Provides
  def provideAuthInfoRepository(passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo]): AuthInfoRepository =
    new DelegableAuthInfoRepository(passwordInfoDAO)

  /** Provides the authenticator service.
    *
    * @param crypter
    *   The crypter implementation.
    * @param idGenerator
    *   The ID generator implementation.
    * @param configuration
    *   The Play configuration.
    * @param clock
    *   The clock instance.
    * @return
    *   The authenticator service.
    */
  @Provides
  def provideAuthenticatorService(
      @Named("authenticator-crypter") crypter: Crypter,
      idGenerator: IDGenerator,
      configuration: Configuration,
      clock: Clock
  ): AuthenticatorService[JWTAuthenticator] = {

    val config = configuration.underlying.as[JWTAuthenticatorSettings]("silhouette.authenticator")
    val encoder = new CrypterAuthenticatorEncoder(crypter)

    new JWTAuthenticatorService(config, None, encoder, idGenerator, clock)
  }

  /** Provides the password hasher registry.
    *
    * @param passwordHasher
    *   The default password hasher implementation.
    * @return
    *   The password hasher registry.
    */
  @Provides
  def providePasswordHasherRegistry(passwordHasher: PasswordHasher): PasswordHasherRegistry =
    new PasswordHasherRegistry(passwordHasher)

  /** Provides the credentials provider.
    *
    * @param authInfoRepository
    *   The auth info repository implementation.
    * @param passwordHasherRegistry
    *   The password hasher registry.
    * @return
    *   The credentials provider.
    */
  @Provides
  def provideCredentialsProvider(
      authInfoRepository: AuthInfoRepository,
      passwordHasherRegistry: PasswordHasherRegistry
  ): CredentialsProvider =
    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)

  /** Provides the dummy authenticator service.
    * @return
    *   The dummy authenticator service.
    */
  @Provides
  def provideDummyAuthenticatorService: AuthenticatorService[DummyAuthenticator] =
    new DummyAuthenticatorService()

  /** See https://www.silhouette.rocks/docs/migration-guide
    */
  @Provides
  def providePasswordDAO(userRepository: UserRepositoryInterface): DelegableAuthInfoDAO[PasswordInfo] =
    new PasswordInfoDAO(
      userRepository
    )
}
