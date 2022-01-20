package config

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Period
import scala.concurrent.duration.FiniteDuration

case class TaskConfiguration(subscription: SubscriptionTaskConfiguration, report: ReportTaskConfiguration)

case class SubscriptionTaskConfiguration(startTime: LocalTime, startDay: DayOfWeek)

case class ReportTaskConfiguration(
    startTime: LocalTime,
    intervalInHours: FiniteDuration,
    noAccessReadingDelay: Period,
    mailReminderDelay: Period,
    reportReminderByPostDelay: Period
)
