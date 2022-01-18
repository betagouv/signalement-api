package config

import java.net.URI
import java.time.Period

case class SignalConsoConfiguration(
    tmpDirectory: String,
    apiURL: URI,
    websiteURL: URI,
    dashboardURL: URI,
    token: TokenConfiguration,
    upload: UploadConfiguration
)

case class UploadConfiguration(allowedExtensions: Seq[String], avScanEnabled: Boolean)

case class TokenConfiguration(
    companyInitDuration: Option[Period],
    companyJoinDuration: Option[Period],
    dgccrfJoinDuration: Option[Period],
    dgccrfDelayBeforeRevalidation: Period
)
