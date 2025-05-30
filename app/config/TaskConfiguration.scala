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
    oldReportsRgpdDeletion: OldReportsRgpdDeletionTaskConfiguration,
    reportReminders: ReportRemindersTaskConfiguration,
    inactiveAccounts: InactiveAccountsTaskConfiguration,
    companyUpdate: CompanyUpdateTaskConfiguration,
    probe: ProbeConfiguration,
    exportReportsToSFTP: ExportReportsToSFTPConfiguration,
    subcategoryLabels: SubcategoryLabelsTaskConfiguration,
    siretExtraction: SiretExtractionConfiguration,
    sampleData: SampleDataConfiguration
)

case class SubscriptionTaskConfiguration(startTime: LocalTime, startDay: DayOfWeek)

case class InactiveAccountsTaskConfiguration(
    startTime: LocalTime,
    inactivePeriod: Period,
    firstReminder: Period,
    secondReminder: Period
)

case class CompanyUpdateTaskConfiguration(
    startTime: LocalTime,
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

case class OldReportsRgpdDeletionTaskConfiguration(
    startTime: LocalTime
)

case class ReportRemindersTaskConfiguration(
    startTime: LocalTime,
    intervalInHours: FiniteDuration,
    mailReminderDelay: Period // 7days.

)

case class SubcategoryLabelsTaskConfiguration(
    startTime: LocalTime,
    interval: FiniteDuration
)

case class SiretExtractionConfiguration(
    interval: FiniteDuration,
    websiteCount: Int
)

case class ProbeConfiguration(active: Boolean)

case class ExportReportsToSFTPConfiguration(filePath: String, startTime: LocalTime)
case class SampleDataConfiguration(active: Boolean = false, startTime: LocalTime)
