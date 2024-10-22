package orchestrators

import cats.implicits.toTraverseOps
import models.report.ReportStatus.SuppressionRGPD
import models.report._
import play.api.Logger
import play.api.libs.json.Json
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.EmailAddress.EmptyEmailAddress

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class RgpdOrchestrator(
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    engagementOrchestrator: EngagementOrchestrator,
    reportRepository: ReportRepositoryInterface,
    reportFileOrchestrator: ReportFileOrchestrator,
    eventRepository: EventRepositoryInterface
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  def deleteRGPD(
      report: Report
  ): Future[Report] = {
    logger.info(s"Emptying report ${report.id} for RGPD")
    val emptiedReport = report.copy(
      firstName = "",
      lastName = "",
      consumerPhone = report.consumerPhone.map(_ => ""),
      consumerReferenceNumber = report.consumerReferenceNumber.map(_ => ""),
      email = EmptyEmailAddress,
      details = List.empty,
      status = SuppressionRGPD
    )
    for {
      _ <- reportRepository.update(emptiedReport.id, emptiedReport)
      proEvents <- eventRepository.getEvents(
        reportId = emptiedReport.id,
        filter = EventFilter(eventType = Some(EventType.PRO), action = Some(ActionEvent.REPORT_PRO_RESPONSE))
      )
      _ <- proEvents.traverse { event =>
        val emptiedDetails = event.details
          .as[ExistingReportResponse]
          .copy(fileIds = List.empty, consumerDetails = "", dgccrfDetails = None)
        eventRepository.update(event.id, event.copy(details = Json.toJson(emptiedDetails)))
      }
      _ <- reportConsumerReviewOrchestrator.deleteDetails(emptiedReport.id)
      _ <- engagementOrchestrator.deleteDetails(emptiedReport.id)
      _ <- reportFileOrchestrator.removeFromReportId(emptiedReport.id)
      _ = logger.info(s"Report ${report.id} was emptied")
    } yield emptiedReport
  }

}
