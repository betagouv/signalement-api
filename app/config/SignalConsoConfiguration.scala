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
    reportsExportLimitMax: Int = 300000,
    reportFileMaxSize: Int,
    reportMaxNumberOfAttachments: Int
)

case class UploadConfiguration(allowedExtensions: Seq[String], avScanEnabled: Boolean, downloadDirectory: String)

case class TokenConfiguration(
    companyInitDuration: Option[Period],
    companyJoinDuration: Option[Period],
    adminJoinDuration: FiniteDuration,
    dgccrfJoinDuration: Period,
    dgccrfDelayBeforeRevalidation: Period,
    dgccrfRevalidationTokenDuration: Option[Period],
    updateEmailAddress: Period
)

case class MobileAppConfiguration(
    minimumAppVersionIos: String,
    minimumAppVersionAndroid: String
)
