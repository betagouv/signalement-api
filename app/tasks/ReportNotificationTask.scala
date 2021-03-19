package tasks

import java.net.URI
import java.time._
import java.time.temporal.ChronoUnit

import actors.EmailActor
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import javax.inject.{Inject, Named}
import models.{Report, ReportFilter, Subscription}
import play.api.{Configuration, Logger}
import repositories.{ReportRepository, SubscriptionRepository}
import utils.Constants.Departments
import utils.{EmailAddress, EmailSubjects}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ReportNotificationTask @Inject()(actorSystem: ActorSystem,
                                       reportRepository: ReportRepository,
                                       subscriptionRepository: SubscriptionRepository,
                                       @Named("email-actor") emailActor: ActorRef,
                                       configuration: Configuration)
                                      (implicit executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())
  implicit val timeout: akka.util.Timeout = 5.seconds

  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.report.notification.start.hour"), configuration.get[Int]("play.tasks.report.notification.start.minute"), 0)

  val startDate = if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime) else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  val departments = Departments.ALL

  actorSystem.scheduler.schedule(initialDelay = initialDelay, 1.days) {
    logger.debug(s"initialDelay - ${initialDelay}");

    if (LocalDate.now.getDayOfWeek == DayOfWeek.valueOf(configuration.get[String]("play.tasks.report.notification.weekly.dayOfWeek"))) {
      runPeriodicNotificationTask(LocalDate.now, Period.ofDays(7))
    }

    runPeriodicNotificationTask(LocalDate.now, Period.ofDays(1))
  }

  def runPeriodicNotificationTask(taskDate: LocalDate, period: Period) = {

    logger.debug(s"Traitement de notification des signalements - period $period")
    logger.debug(s"taskDate - ${taskDate}");

    for {
      subscriptions <- subscriptionRepository.listForFrequency(period)
      reports <- reportRepository.getReports(
          0,
          10000,
          ReportFilter(start = Some(taskDate.minus(period)), end = Some(taskDate))
      )
    } yield {
      subscriptions.foreach(subscription => {

        sendMailReportNotification(
          subscription._2,
          subscription._1,
          reports.entities
            .filter(report => subscription._1.departments.isEmpty || subscription._1.departments.map(Some(_)).contains(report.companyPostalCode.flatMap(Departments.fromPostalCode(_))))
            .filter(report => subscription._1.categories.isEmpty || subscription._1.categories.map(_.value).contains(report.category))
            .filter(report => subscription._1.sirets.isEmpty || subscription._1.sirets.map(Some(_)).contains(report.companySiret))
            .filter(report => subscription._1.countries.isEmpty || subscription._1.countries.map(Some(_)).contains(report.companyCountry))
            .filter(report => subscription._1.tags.isEmpty || subscription._1.tags.intersect(report.tags).nonEmpty),
          taskDate.minus(period)
        )
      })
    }
  }

  private def sendMailReportNotification(email: EmailAddress, subscription: Subscription, reports: List[Report], startDate: LocalDate) = {

    if (reports.length > 0) {

      logger.debug(s"sendMailReportNotification $email - abonnement ${subscription.id} - ${reports.length} signalements")

      emailActor ? EmailActor.EmailRequest(
        from = configuration.get[EmailAddress]("play.mail.from"),
        recipients = Seq(email),
        subject = EmailSubjects.REPORT_NOTIF_DGCCRF(reports.length),
        bodyHtml = views.html.mails.dgccrf.reportNotification(subscription, reports, startDate).toString
      )
    }
  }
}
