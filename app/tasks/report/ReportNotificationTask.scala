package tasks.report

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import config.TaskConfiguration
import models.Subscription
import models.User
import models.UserRole
import models.report.PreFilter
import models.report.Report
import models.report.ReportFile
import models.report.ReportFilter
import play.api.Logger
import repositories.report.ReportRepositoryInterface
import repositories.subscription.SubscriptionRepositoryInterface
import repositories.tasklock.TaskLockRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.Email.DgccrfReportNotification
import services.MailService
import tasks.report.ReportNotificationTask.refineReportBasedOnSubscriptionFilters
import tasks.ScheduledTask
import utils.Constants.Departments

import java.time._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import utils.Logs.RichLogger

import scala.collection.SortedMap

class ReportNotificationTask(
    actorSystem: ActorSystem,
    reportRepository: ReportRepositoryInterface,
    subscriptionRepository: SubscriptionRepositoryInterface,
    userRepository: UserRepositoryInterface,
    mailService: MailService,
    taskConfiguration: TaskConfiguration,
    taskLockRepository: TaskLockRepositoryInterface
)(implicit executionContext: ExecutionContext)
    extends ScheduledTask(3, "report_notification_task", taskLockRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = taskConfiguration.subscription.startTime
  override val interval: FiniteDuration = 1.day

  override def runTask(): Future[Unit] = {
    val now                      = OffsetDateTime.now()
    val isWeeklySubscriptionsDay = LocalDate.now().getDayOfWeek == taskConfiguration.subscription.startDay
    for {
      _ <-
        if (isWeeklySubscriptionsDay)
          runPeriodicNotificationTask(now, Period.ofDays(7))
        else Future.successful(())
      _ <- runPeriodicNotificationTask(now, Period.ofDays(1))
    } yield ()
  }

  val departments = Departments.ALL

  def runPeriodicNotificationTask(now: OffsetDateTime, period: Period): Future[Unit] = {
    val end   = now
    val start = end.minus(period)
    logger.debug(s"Traitement de notification des signalements - period $period - $start to $end")
    val executionFuture = for {
      subscriptionsWithMaybeEmails <- subscriptionRepository.listForFrequency(period)
      subscriptionsWithEmails = subscriptionsWithMaybeEmails.collect { case (s, Some(ea)) => (s, ea) }
      _ = logger.debug(s"Found ${subscriptionsWithEmails.size} subscriptions to handle (period $period)")
      reportsWithFiles <- reportRepository.getReportsWithFiles(
        None,
        ReportFilter(
          start = Some(start),
          end = Some(end)
        )
      )
      _ = logger.debug(s"Found ${reportsWithFiles.size} reports for this period ($period)")
      subscriptionsEmailAndReports <- Future.sequence(subscriptionsWithEmails.map { case (subscription, emailAddress) =>
        refineReportBasedOnSubscriptionFilters(userRepository, reportsWithFiles, subscription).map { filteredReport =>
          (subscription, emailAddress, filteredReport)
        }
      })
      subscriptionEmailAndNonEmptyReports = subscriptionsEmailAndReports.filter(_._3.nonEmpty)
      _ = logger.debug(
        s"We have ${subscriptionEmailAndNonEmptyReports.size} emails of notifications to send (period $period)"
      )
      _ <- subscriptionEmailAndNonEmptyReports.map { case (subscription, emailAddress, filteredReport) =>
        logger.infoWithTitle(
          "report_notification_task_item",
          s"Sending a subscription notification email to ${emailAddress}"
        )
        mailService.send {
          DgccrfReportNotification(
            List(emailAddress),
            subscription,
            filteredReport.toList,
            start.toLocalDate
          )
        }
      }.sequence
    } yield ()
    executionFuture
      .onComplete {
        case Success(_) =>
          logger.info(s"Notifications task ran successfully for period $period")
        case Failure(err) =>
          logger.error(
            s"Failure when running reports notification task for period $period at $now",
            err
          )
      }
    executionFuture
  }
}

object ReportNotificationTask {

  private def userFromSubscription(
      userRepository: UserRepositoryInterface,
      subscription: Subscription
  ): Future[Option[User]] = subscription.userId match {
    case Some(userId) => userRepository.get(userId)
    case None         => Future.successful(None)
  }

  def refineReportBasedOnSubscriptionFilters(
      userRepository: UserRepositoryInterface,
      reportsWithFiles: SortedMap[Report, List[ReportFile]],
      subscription: Subscription
  )(implicit ec: ExecutionContext): Future[SortedMap[Report, List[ReportFile]]] =
    userFromSubscription(userRepository, subscription).map { maybeUser =>
      reportsWithFiles
        .filter { case (report, _) =>
          maybeUser.map(_.userRole) match {
            case Some(UserRole.DGAL) =>
              PreFilter.DGALFilter.category
                .forall(_.entryName == report.category) || (if (PreFilter.DGALFilter.tags.isEmpty) true
                                                            else
                                                              PreFilter.DGALFilter.tags
                                                                .intersect(report.tags)
                                                                .nonEmpty)
            case Some(UserRole.DGCCRF)        => true
            case Some(UserRole.Admin)         => true
            case Some(UserRole.Professionnel) => true
            case None                         => true
          }
        }
        .filter { case (report, _) =>
          subscription.departments.isEmpty || (report.companyAddress.country.isEmpty && subscription.departments
            .map(Some(_))
            .contains(report.companyAddress.postalCode.flatMap(Departments.fromPostalCode)))
        }
        .filter { case (report, _) =>
          subscription.categories.isEmpty || subscription.categories.map(_.entryName).contains(report.category)
        }
        .filter { case (report, _) =>
          subscription.sirets.isEmpty || subscription.sirets.map(Some(_)).contains(report.companySiret)
        }
        .filter { case (report, _) =>
          subscription.countries.isEmpty || subscription.countries
            .map(Some(_))
            .contains(report.companyAddress.country)
        }
        .filter { case (report, _) =>
          subscription.withTags.isEmpty || subscription.withTags.intersect(report.tags).nonEmpty
        }
        .filter { case (report, _) =>
          subscription.withoutTags.isEmpty || subscription.withoutTags.intersect(report.tags).isEmpty
        }
    }

}
