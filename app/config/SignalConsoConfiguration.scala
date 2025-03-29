package config

import java.net.URI
import java.time.Period
import scala.concurrent.duration.FiniteDuration

case class SignalConsoConfiguration(
    tmpDirectory: String,
    apiURL: URI,
    websiteURL: URI,
    dashboardURL: URI,
    token: TokenConfiguration,
    upload: UploadConfiguration,
    mobileApp: MobileAppConfiguration,
    reportsExportLimitMax: Int,
    reportsExportPdfLimitMax: Int,
    reportsListLimitMax: Int,
    reportFileMaxSize: Int,
    reportMaxNumberOfAttachments: Int,
    enableRateLimit: Boolean,
    antivirusServiceConfiguration: AntivirusServiceConfiguration
)

case class UploadConfiguration(allowedExtensions: Seq[String], avScanEnabled: Boolean, downloadDirectory: String)

case class TokenConfiguration(
    companyInitDuration: Option[Period],
    companyJoinDuration: Option[Period],
    adminJoinDuration: FiniteDuration,
    dgccrfJoinDuration: Period,
    dgccrfDelayBeforeRevalidation: Period,
    dgccrfRevalidationTokenDuration: Option[Period],
    updateEmailAddressDuration: Period
)

case class MobileAppConfiguration(
    minimumAppVersionIos: String,
    minimumAppVersionAndroid: String
)

case class AntivirusServiceConfiguration(
    antivirusApiUrl: String,
    antivirusApiKey: String,
    active: Boolean,
    bypassScan: Boolean
)
