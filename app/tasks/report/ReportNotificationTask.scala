package tasks.report

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import config.TaskConfiguration
import models.ReportFilter
import play.api.Logger
import repositories.ReportRepository
import repositories.SubscriptionRepository
import services.Email.DgccrfReportNotification
import services.MailService
import utils.Constants.Departments

import java.time._
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class ReportNotificationTask @Inject() (
    actorSystem: ActorSystem,
    reportRepository: ReportRepository,
    subscriptionRepository: SubscriptionRepository,
    mailService: MailService,
    taskConfiguration: TaskConfiguration
)(implicit executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)
  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime = taskConfiguration.subscription.startTime

  val startDate =
    if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime)
    else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  val departments = Departments.ALL

  actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = initialDelay, 1.days) { () =>
    logger.debug(s"initialDelay - ${initialDelay}");

    if (LocalDate.now.getDayOfWeek == taskConfiguration.subscription.startDay) {
      runPeriodicNotificationTask(LocalDate.now, Period.ofDays(7))
    }

    runPeriodicNotificationTask(LocalDate.now, Period.ofDays(1))
  }

  def runPeriodicNotificationTask(taskDate: LocalDate, period: Period): Future[Unit] = {

    logger.debug(s"Traitement de notification des signalements - period $period")
    logger.debug(s"taskDate - ${taskDate}");

    for {
      subscriptions <- subscriptionRepository.listForFrequency(period)
      reports <- reportRepository.getReports(
        ReportFilter(start = Some(taskDate.minus(period)), end = Some(taskDate)),
        Some(0),
        Some(10000)
      )
      subscriptionsEmailAndReports = subscriptions.map { case (subscription, emailAddress) =>
        val filteredReport = reports.entities
          .filter(report =>
            subscription.departments.isEmpty || subscription.departments
              .map(Some(_))
              .contains(report.companyAddress.postalCode.flatMap(Departments.fromPostalCode))
          )
          .filter(report =>
            subscription.categories.isEmpty || subscription.categories.map(_.value).contains(report.category)
          )
          .filter(report =>
            subscription.sirets.isEmpty || subscription.sirets.map(Some(_)).contains(report.companySiret)
          )
          .filter(report =>
            subscription.countries.isEmpty || subscription.countries
              .map(Some(_))
              .contains(report.companyAddress.country)
          )
          .filter(report => subscription.tags.isEmpty || subscription.tags.intersect(report.tags).nonEmpty)
        (subscription, emailAddress, filteredReport)
      }
      _ <- subscriptionsEmailAndReports.map { case (subscription, emailAddress, filteredReport) =>
        mailService.send(
          DgccrfReportNotification(
            List(emailAddress),
            subscription,
            filteredReport,
            taskDate.minus(period)
          )
        )
      }.sequence
    } yield ()
  }
}
