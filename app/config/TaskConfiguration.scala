package config

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Period
import scala.concurrent.duration.FiniteDuration

case class TaskConfiguration(
    active: Boolean,
    subscription: SubscriptionTaskConfiguration,
    reportClosure: ReportClosureTaskConfiguration,
    reportReminders: ReportRemindersTaskConfiguration,
    inactiveAccounts: InactiveAccountsTaskConfiguration,
    companyUpdate: CompanyUpdateTaskConfiguration
)

case class SubscriptionTaskConfiguration(startTime: LocalTime, startDay: DayOfWeek)

case class InactiveAccountsTaskConfiguration(startTime: LocalTime, inactivePeriod: Period)

case class CompanyUpdateTaskConfiguration(
    etablissementApiUrl: String,
    etablissementApiKey: String
)

case class ReportClosureTaskConfiguration(
    startTime: LocalTime
)

case class ReportRemindersTaskConfiguration(
    startTime: LocalTime,
    intervalInHours: FiniteDuration,
    mailReminderDelay: Period // 7days.

)
