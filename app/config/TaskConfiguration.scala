package config

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Period
import scala.concurrent.duration.FiniteDuration

case class TaskConfiguration(
    active: Boolean,
    subscription: SubscriptionTaskConfiguration,
    report: ReportTaskConfiguration,
    inactiveAccounts: InactiveAccountsTaskConfiguration,
    companyUpdate: CompanyUpdateTaskConfiguration
)

case class SubscriptionTaskConfiguration(startTime: LocalTime, startDay: DayOfWeek)

case class InactiveAccountsTaskConfiguration(startTime: LocalTime, inactivePeriod: Period)

case class CompanyUpdateTaskConfiguration(
    localSync: Boolean,
    etablissementApiUrl: String,
    etablissementApiKey: String
)

case class ReportTaskConfiguration(
    startTime: LocalTime, // 04:00
    intervalInHours: FiniteDuration, // 24Hours
    noAccessReadingDelay: Period, // 60days
    mailReminderDelay: Period, // 7days
    reportReminderByPostDelay: Period // 28days
)
