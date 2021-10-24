package config

import play.api.Configuration
import utils.EmailAddress

import java.net.URI
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class AppConfigReport(
    noAccessReadingDelay: Period,
    mailReminderDelay: Period,
    reportReminderByPostDelay: Period
)

case class AppConfigStats(
    backofficeAdminStartDate: LocalDate,
    backofficeProStartDate: LocalDate,
    globalStatsCutoff: Option[Duration]
)

case class AppConfigUpload(
    allowedExtensions: Seq[String],
    avScanEnabled: Boolean
)

case class AppConfigMail(
    from: EmailAddress,
    contactAddress: EmailAddress,
    skipReportEmailValidation: Boolean,
    ccrfEmailSuffix: String
)

case class AppConfigTask(
    reportNotificationStartTime: LocalTime,
    reportNotificationStartDay: DayOfWeek,
    reminderStartTime: LocalTime,
    reminderIntervalHours: FiniteDuration
)

case class AppConfigAkkaCredentials(
    keyId: String,
    secretKey: String
)

case class AppConfigTokens(
    companyInitDuration: Option[Period],
    companyJoinDuration: Option[Period],
    dgccrfJoinDuration: Option[Period],
    dgccrfDelayBeforeRevalidation: Period
)

case class AppConfig(
    zoneId: ZoneId,
    tmpDirectory: String,
    s3BucketName: String,
    signalConsoApiUrl: URI,
    signalConsoAppUrl: URI,
    signalConsoDashboardUrl: URI,
    tokens: AppConfigTokens,
    upload: AppConfigUpload,
    report: AppConfigReport,
    mail: AppConfigMail,
    stats: AppConfigStats,
    task: AppConfigTask,
    akka: AppConfigAkkaCredentials
)

@Singleton
class AppConfigLoader @Inject() (c: Configuration) {
  lazy val get: AppConfig = AppConfig(
    zoneId = ZoneId.of(c.get[String]("play.zoneId")),
    tmpDirectory = c.get[String]("app.tmpDirectory"),
    s3BucketName = c.get[String]("app.s3BucketName"),
    signalConsoApiUrl = c.get[URI]("play.application.url"),
    signalConsoAppUrl = c.get[URI]("app.website.url"),
    signalConsoDashboardUrl = c.get[URI]("app.website.url"),
    mail = AppConfigMail(
      from = c.get[EmailAddress]("app.mail.from"),
      contactAddress = c.get[EmailAddress]("app.mail.contactAddress"),
      skipReportEmailValidation = c.get[Boolean]("app.mail.skipReportEmailValidation"),
      ccrfEmailSuffix = c.get[String]("app.mail.ccrfEmailSuffix")
    ),
    report = AppConfigReport(
      noAccessReadingDelay = Period.parse(c.get[String]("app.report.noAccessReadingDelay")),
      mailReminderDelay = Period.parse(c.get[String]("app.report.mailReminderDelay")),
      reportReminderByPostDelay = Period.parse(c.get[String]("app.report.reportReminderByPostDelay"))
    ),
    upload = AppConfigUpload(
      avScanEnabled = c.get[Boolean]("app.upload.avScanEnabled"),
      allowedExtensions = c.get[Seq[String]]("app.upload.allowedExtensions")
    ),
    tokens = AppConfigTokens(
      companyInitDuration = c.getOptional[String]("app.token.companyInitDuration").map(Period.parse),
      companyJoinDuration = c.getOptional[String]("app.token.companyJoinDuration").map(Period.parse),
      dgccrfJoinDuration = c.getOptional[String]("app.token.dgccrfJoinDuration").map(Period.parse),
      dgccrfDelayBeforeRevalidation = Period.parse(c.get[String]("app.token.dgccrfDelayBeforeRevalidation"))
    ),
    stats = AppConfigStats(
      backofficeAdminStartDate = LocalDate.parse(c.get[String]("app.stats.backofficeAdminStartDate")),
      backofficeProStartDate = LocalDate.parse(c.get[String]("app.stats.backofficeProStartDate")),
      globalStatsCutoff = c.getOptional[String]("app.stats.globalStatsCutoff").map(Duration.parse)
    ),
    task = AppConfigTask(
      reportNotificationStartTime = LocalTime.of(
        c.get[Int]("app.task.report.notification.start.hour"),
        c.get[Int]("app.task.report.notification.start.minute"),
        0
      ),
      reportNotificationStartDay = DayOfWeek.valueOf(c.get[String]("app.task.report.notification.weekly.dayOfWeek")),
      reminderStartTime = LocalTime.of(
        c.get[Int]("app.task.reminder.start.hour"),
        c.get[Int]("app.task.reminder.start.minute"),
        0
      ),
      reminderIntervalHours = c.get[Int]("app.task.reminder.intervalInHours").hours
    ),
    akka = AppConfigAkkaCredentials(
      keyId = c.get[String]("alpakka.s3.aws.credentials.access-key-id"),
      secretKey = c.get[String]("alpakka.s3.aws.credentials.secret-access-key")
    )
  )
}
