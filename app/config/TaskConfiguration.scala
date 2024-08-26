package config

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Period
import scala.concurrent.duration.FiniteDuration

case class TaskConfiguration(
    active: Boolean,
    subscription: SubscriptionTaskConfiguration,
    reportClosure: ReportClosureTaskConfiguration,
    orphanReportFileDeletion: OrphanReportFileDeletionTaskConfiguration,
    oldReportExportDeletion: OldReportExportDeletionTaskConfiguration,
    reportReminders: ReportRemindersTaskConfiguration,
    inactiveAccounts: InactiveAccountsTaskConfiguration,
    companyUpdate: CompanyUpdateTaskConfiguration,
    probe: ProbeConfiguration,
    exportReportsToSFTP: ExportReportsToSFTPConfiguration
)

case class SubscriptionTaskConfiguration(startTime: LocalTime, startDay: DayOfWeek)

case class InactiveAccountsTaskConfiguration(
    startTime: LocalTime,
    inactivePeriod: Period,
    firstReminder: Period,
    secondReminder: Period
)

case class CompanyUpdateTaskConfiguration(
    etablissementApiUrl: String,
    etablissementApiKey: String
)

case class ReportClosureTaskConfiguration(
    startTime: LocalTime
)

case class OrphanReportFileDeletionTaskConfiguration(
    startTime: LocalTime
)

case class OldReportExportDeletionTaskConfiguration(
    startTime: LocalTime
)

case class ReportRemindersTaskConfiguration(
    startTime: LocalTime,
    intervalInHours: FiniteDuration,
    mailReminderDelay: Period // 7days.

)

case class ProbeConfiguration(active: Boolean, recipients: List[String])

case class ExportReportsToSFTPConfiguration(filePath: String, startTime: LocalTime)
