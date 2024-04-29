package tasks

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import config.TaskConfiguration
import models.event.Event
import models.report.Report
import models.report.ReportResponse
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.json.JsResult
import repositories.company.CompanyRepositoryInterface
import repositories.engagement.EngagementRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.emails.EmailDefinitionsConsumer.ConsumerProEngagementReview
import services.emails.MailService

import java.time.LocalDate
import java.time.LocalTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class EngagementEmailTask(
    mailService: MailService,
    companyRepository: CompanyRepositoryInterface,
    engagementRepository: EngagementRepositoryInterface,
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    messagesApi: MessagesApi
)(implicit
    executionContext: ExecutionContext
) extends ScheduledTask(6, "engagement_email_task", taskRepository, actorSystem, taskConfiguration) {

  override val logger: Logger           = Logger(this.getClass)
  override val startTime: LocalTime     = LocalTime.of(2, 0)
  override val interval: FiniteDuration = 1.day

  private def sendEmail(
      report: Report,
      promiseEvent: Event,
      resolutionEvent: Option[Event]
  ) =
    for {
      maybeCompany   <- report.companySiret.map(companyRepository.findBySiret).flatSequence
      reportResponse <- Future.fromTry(JsResult.toTry(promiseEvent.details.validate[ReportResponse]))
      _ <- mailService.send(
        ConsumerProEngagementReview.Email(
          report,
          maybeCompany,
          reportResponse,
          resolutionEvent.isDefined,
          messagesApi
        )
      )
    } yield ()

  override def runTask(): Future[Unit] = {
    val today = LocalDate.now()
    for {
      engagements <- engagementRepository.listEngagementsExpiringAt(today)
      _ = logger.debug(s"${engagements.length} engagements found in DB handle")
      _ <- engagements.map { case (((_, report), promiseEvent), resolutionEvent) =>
        sendEmail(report, promiseEvent, resolutionEvent)
      }.sequence
    } yield ()
  }
}
