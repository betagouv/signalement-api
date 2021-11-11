package config

import utils.EmailAddress

import java.net.URI
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import scala.concurrent.duration.FiniteDuration

case class SignalConsoConfiguration(
    zoneId: ZoneId,
    tmpDirectory: String,
    amazonBucketName: String,
    apiURL: URI,
    websiteURL: URI,
    dashboardURL: URI,
    token: TokenConfiguration,
    upload: UploadConfiguration,
    report: ReportConfiguration,
    mail: EmailConfiguration,
    task: TaskConfiguration
)

case class UploadConfiguration(allowedExtensions: Seq[String], avScanEnabled: Boolean)

case class TokenConfiguration(
    companyInitDuration: Option[Period],
    companyJoinDuration: Option[Period],
    dgccrfJoinDuration: Option[Period],
    dgccrfDelayBeforeRevalidation: Period
)

case class ReportConfiguration(
    noAccessReadingDelay: Period,
    mailReminderDelay: Period,
    reportReminderByPostDelay: Period
)

case class EmailConfiguration(
    from: EmailAddress,
    contactAddress: EmailAddress,
    skipReportEmailValidation: Boolean,
    ccrfEmailSuffix: String
)

case class TaskConfiguration(report: ReportTaskConfiguration, reminder: ReminderTaskConfiguration)
case class ReportTaskConfiguration(startTime: LocalTime, startDay: DayOfWeek)
case class ReminderTaskConfiguration(startTime: LocalTime, intervalInHours: FiniteDuration)
