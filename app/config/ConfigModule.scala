package config

import com.google.inject.AbstractModule
import pureconfig.ConfigConvert
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import utils.EmailAddress

import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ConfigModule extends AbstractModule {

  override def configure() = {

    implicit val localTimeInstance: ConfigConvert[LocalTime] = localTimeConfigConvert(DateTimeFormatter.ISO_TIME)
    implicit val personReader: ConfigReader[EmailAddress] = deriveReader[EmailAddress]

    val configuration = ConfigSource.default
      .loadOrThrow[ApplicationConfiguration]
      .app

    bind(classOf[EmailConfiguration]).toInstance(configuration.mail)
    bind(classOf[TokenConfiguration]).toInstance(configuration.token)
  }
}
