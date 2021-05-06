package tasks

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime}

import akka.actor.{ActorRef, ActorSystem}
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named}
import play.api.{Configuration, Logger}
import utils.Constants.ReportStatus._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class EnterpriseDatabaseSync @Inject()(
  actorSystem: ActorSystem,
  @Named("enterprise-database-sync") actorRef: ActorRef,
  val silhouette: Silhouette[AuthEnv],
  val silhouetteAPIKey: Silhouette[APIKeyEnv],
  configuration: Configuration
)(
  implicit val executionContext: ExecutionContext
) {


  val logger: Logger = Logger(this.getClass)

  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime            = LocalTime.of(configuration.get[Int]("play.tasks.reminder.start.hour"), configuration.get[Int]("play.tasks.reminder.start.minute"), 0)
  val interval             = configuration.get[Int]("play.tasks.reminder.intervalInHours").hours
  val noAccessReadingDelay = java.time.Period.parse(configuration.get[String]("play.reports.noAccessReadingDelay"))
  val mailReminderDelay    = java.time.Period.parse(configuration.get[String]("play.reports.mailReminderDelay"))

  val startDate    = if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime) else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

//  actorSystem.scheduler.scheduleAtFixedRate(startDate = initialDelay = initialDelay, interval = interval) {
//    logger.debug(s"initialDelay - ${initialDelay}");
//  }
}
