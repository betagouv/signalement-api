package config

import com.google.inject.AbstractModule
import play.api.Configuration
import play.api.Environment
import pureconfig.ConfigConvert
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.configurable.localTimeConfigConvert
import pureconfig.generic.auto._
import pureconfig.generic.semiauto.deriveReader
import utils.EmailAddress

import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ConfigModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure() = {

    val _ = environment
    implicit val localTimeInstance: ConfigConvert[LocalTime] = localTimeConfigConvert(DateTimeFormatter.ISO_TIME)
    implicit val personReader: ConfigReader[EmailAddress] = deriveReader[EmailAddress]
    val csvStringListReader = ConfigReader[String].map(_.split(",").toList)
    implicit val stringListReader = ConfigReader[List[String]].orElse(csvStringListReader)

    val applicationConfiguration = ConfigSource.default.loadOrThrow[ApplicationConfiguration]

    lazy val s3: BucketConfiguration = BucketConfiguration(
      keyId = configuration.get[String]("alpakka.s3.aws.credentials.access-key-id"),
      secretKey = configuration.get[String]("alpakka.s3.aws.credentials.secret-access-key"),
      amazonBucketName = applicationConfiguration.amazonBucketName
    )

    bind(classOf[EmailConfiguration]).toInstance(applicationConfiguration.mail)
    bind(classOf[TaskConfiguration]).toInstance(applicationConfiguration.task)
    bind(classOf[BucketConfiguration]).toInstance(s3)
    bind(classOf[SignalConsoConfiguration]).toInstance(applicationConfiguration.app)
    bind(classOf[TokenConfiguration]).toInstance(applicationConfiguration.app.token)
    bind(classOf[UploadConfiguration]).toInstance(applicationConfiguration.app.upload)
  }
}

case class ApplicationConfiguration(
    app: SignalConsoConfiguration,
    mail: EmailConfiguration,
    task: TaskConfiguration,
    amazonBucketName: String
)
