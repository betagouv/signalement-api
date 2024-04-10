package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError.PromiseOfActionNotFound
import controllers.error.AppError.ReportNotFound
import models.User
import models.event.Event
import models.promise.PromiseOfActionApi
import models.promise.PromiseOfActionId
import models.report.ReportResponse
import play.api.libs.json.Json
import repositories.event.EventRepositoryInterface
import repositories.promise.PromiseOfActionRepositoryInterface
import repositories.report.ReportRepositoryInterface
import utils.Constants.ActionEvent
import utils.Constants.EventType

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PromiseOfActionOrchestrator(
    promiseOfActionRepository: PromiseOfActionRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    eventRepository: EventRepositoryInterface,
    reportRepository: ReportRepositoryInterface
)(implicit val executionContext: ExecutionContext) {
  def listForUser(proUser: User): Future[Seq[PromiseOfActionApi]] =
    for {
      companiesWithAccesses <- companiesVisibilityOrchestrator.fetchVisibleCompanies(proUser)
      promises <- promiseOfActionRepository.listPromisesWithEventsAndReport(
        Some(proUser.userRole),
        companiesWithAccesses.map(_.company.id)
      )
    } yield promises.flatMap { case ((report, promise), event) =>
      for {
        reportResponse  <- event.details.asOpt[ReportResponse]
        responseDetails <- reportResponse.responseDetails
      } yield PromiseOfActionApi(
        promise.id,
        report,
        event.creationDate.plusDays(8),
        responseDetails,
        reportResponse.otherResponseDetails
      )
    }

  def resolve(proUser: User, promiseId: PromiseOfActionId): Future[Unit] =
    for {
      maybePromise          <- promiseOfActionRepository.get(promiseId)
      promise               <- maybePromise.liftTo[Future](PromiseOfActionNotFound(promiseId))
      maybeReport           <- reportRepository.getFor(Some(proUser.userRole), promise.reportId)
      report                <- maybeReport.liftTo[Future](ReportNotFound(promise.reportId))
      companiesWithAccesses <- companiesVisibilityOrchestrator.fetchVisibleCompanies(proUser)
      _ <- report.report.companyId match {
        case Some(companyId) if companiesWithAccesses.map(_.company.id).contains(companyId) => Future.unit
        case _ => Future.failed(ReportNotFound(promise.reportId))
      }
      event <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.report.id),
          report.report.companyId,
          Some(proUser.id),
          OffsetDateTime.now(),
          EventType.PRO,
          ActionEvent.REPORT_PRO_PROMISE_OF_ACTION_HONOURED,
          Json.obj()
        )
      )
      _ <- promiseOfActionRepository.honour(promiseId, event.id)
    } yield ()

}
