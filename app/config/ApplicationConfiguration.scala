package config

import authentication.CookieAuthenticator.CookieAuthenticatorSettings
import authentication.JcaCrypterSettings
import authentication.JcaSignerSettings

case class ApplicationConfiguration(
    app: SignalConsoConfiguration,
    mail: EmailConfiguration,
    task: TaskConfiguration,
    flyway: FlywayConfiguration,
    siretExtractor: SiretExtractorConfiguration,
    gs1: GS1Configuration,
    amazonBucketName: String,
    crypter: JcaCrypterSettings,
    signer: JcaSignerSettings,
    cookie: CookieAuthenticatorSettings,
    socialBlade: SocialBladeClientConfiguration,
    websiteApi: WebsiteApiConfiguration
)
