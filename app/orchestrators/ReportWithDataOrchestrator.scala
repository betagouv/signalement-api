package orchestrators

import config.SignalConsoConfiguration
import models.User
import models.company.Company
import models.event.Event
import models.report.ExistingReportResponse
import models.report.Report
import models.report.ReportFileApi
import models.report.ReportFilter
import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReview
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import utils.Constants

import scala.concurrent.ExecutionContext

case class ReportWithData(
    report: Report,
    maybeCompany: Option[Company],
    events: Seq[(Event, Option[User])],
    responseOption: Option[ExistingReportResponse],
    consumerReviewOption: Option[ResponseConsumerReview],
    engagementReviewOption: Option[EngagementReview],
    companyEvents: Seq[(Event, Option[User])],
    files: Seq[ReportFileApi]
)

class ReportWithDataOrchestrator(
    companyRepository: CompanyRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    reportOrchestrator: ReportOrchestrator,
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    signalConsoConfiguration: SignalConsoConfiguration
)(implicit val executionContext: ExecutionContext) {
  val logger = Logger(this.getClass)

  def getReportsFull(reportFilter: ReportFilter, user: User) =
    for {
      reportsWithFiles <- reportOrchestrator
        .getReportsForUser(
          user,
          reportFilter,
          None,
          None,
          None,
          signalConsoConfiguration.reportsExportPdfLimitMax
        )
        .map(_.entities)
      reportIds    = reportsWithFiles.map(_.report.id)
      companySiret = reportsWithFiles.flatMap(_.report.companySiret)
      companyIds   = reportsWithFiles.flatMap(_.report.companyId)
      consumerReviewsMap <- reportConsumerReviewOrchestrator.getReviews(reportIds)
      companies          <- companyRepository.findBySirets(companySiret).map(l => l.map(c => (c.siret, c)).toMap)
      eventsByCompanyId  <- eventRepository.getCompaniesEventsWithUsers(companyIds, EventFilter.Empty)
      eventsByReportId <- eventRepository
        .getEventsWithUsers(reportIds, EventFilter.Empty)
        .map(events =>
          events
            .collect { case (event @ Event(_, Some(reportId), _, _, _, _, _, _), user) =>
              (reportId, (event, user))
            }
            .groupMap(_._1)(_._2)
        )
    } yield reportsWithFiles.map { reportWithFiles =>
      val report = reportWithFiles.report
      val events = eventsByReportId.getOrElse(report.id, List.empty)
      val responseOption = events
        .map(_._1)
        .find(_.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
        .map(_.details)
        .map(_.as[ExistingReportResponse])

      val companyEvents = report.companyId
        .map { c =>
          eventsByCompanyId.filter(_._1.companyId.contains(c))
        }
        .getOrElse(List.empty)

      ReportWithData(
        report = report,
        maybeCompany = report.companySiret.flatMap(companies.get),
        events = events,
        responseOption = responseOption,
        consumerReviewOption = consumerReviewsMap.get(report.id).flatten,
        engagementReviewOption = reportWithFiles.engagementReview,
        companyEvents = companyEvents,
        files = reportWithFiles.files.map(ReportFileApi.build)
      )
    }

}
