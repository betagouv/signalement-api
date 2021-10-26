package config

import play.api.Configuration
import pureconfig._
import pureconfig.configurable.localDateConfigConvert
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import utils.EmailAddress

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

case class ApplicationConfiguration(
    app: SignalConsoConfiguration
)

case class AppConfigAkkaCredentials(
    keyId: String,
    secretKey: String
)
@Singleton
class AppConfigLoader @Inject() (c: Configuration) {

  implicit val localDateConvert = localDateConfigConvert(DateTimeFormatter.ISO_DATE)
  implicit val localTimeInstance: ConfigConvert[LocalTime] = localTimeConfigConvert(DateTimeFormatter.ISO_TIME)
  implicit val personReader = deriveReader[EmailAddress]

  lazy val signalConsoConfiguration: SignalConsoConfiguration =
    ConfigSource.default
      .loadOrThrow[ApplicationConfiguration]
      .app

  lazy val AlpakkaS3Configuration: AppConfigAkkaCredentials = AppConfigAkkaCredentials(
    keyId = c.get[String]("alpakka.s3.aws.credentials.access-key-id"),
    secretKey = c.get[String]("alpakka.s3.aws.credentials.secret-access-key")
  )

}
