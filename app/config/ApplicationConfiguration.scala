package config

import utils.auth.CookieAuthenticator.CookieAuthenticatorSettings
import utils.auth.JcaCrypterSettings
import utils.auth.JcaSignerSettings

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
    cookie: CookieAuthenticatorSettings
)
