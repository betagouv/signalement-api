package tasks

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime}

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.{Configuration, Environment, Logger}
import repositories.ReportDataRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class ReportDataTask @Inject()(actorSystem: ActorSystem,
                               reportDataRepository: ReportDataRepository,
                               configuration: Configuration,
                               environment: Environment)
                              (implicit val executionContext: ExecutionContext) {


  val logger: Logger = Logger(this.getClass)

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.report.data.start.hour"), configuration.get[Int]("play.tasks.report.data.start.minute"), 0)
  val interval = configuration.get[Int]("play.tasks.report.data.intervalInHours").hours

  val startDate = if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime) else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds


  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {

    val taskDate = LocalDate.now

    logger.debug("Traitement de mise à jour des reportData automatique")
    logger.debug(s"taskDate - ${taskDate}");

    for {
      _ <- reportDataRepository.updateReportReadDelay
      _ <- reportDataRepository.updateReportResponseDelay
    } yield Unit
  }
}
