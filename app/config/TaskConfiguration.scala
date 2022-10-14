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
    localSync: Boolean,
    etablissementApiUrl: String,
    etablissementApiKey: String
)

// TODO check toutes les usages de ces trucs là ailleurs que dans la task
//case class ReportTaskConfiguration(
//    reportReminderByPostDelay: Period, // 28days
//    noAccessClosureDelay: Period, // 60days
//    withAccessClosureDelay: Period, // 25days
//    mailReminderDelay: Period // 7days.
//)

case class ReportClosureTaskConfiguration(
    startTime: LocalTime
)

case class ReportRemindersTaskConfiguration(
    startTime: LocalTime,
    intervalInHours: FiniteDuration,
    mailReminderDelay: Period // 7days.

)
