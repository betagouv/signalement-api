package controllers

import cats.implicits.toTraverseOps
import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppError.SpammerEmailBlocked
import models._
import models.report.ReportAction
import models.report.ReportCompany
import models.report.ReportConsumerUpdate
import models.report.ReportDraft
import models.report.ReportResponse
import models.report.ReportWithFiles
import orchestrators.EventsOrchestratorInterface
import orchestrators.ReportOrchestrator
import orchestrators.ReportWithDataOrchestrator
import play.api.Logger
import play.api.i18n.Lang
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportfile.ReportFileRepositoryInterface
import services.PDFService
import utils.Constants.ActionEvent._
import utils.FrontRoute
import utils.QueryStringMapper
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission
import utils.silhouette.auth.WithRole

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportController(
    reportOrchestrator: ReportOrchestrator,
    eventsOrchestrator: EventsOrchestratorInterface,
    reportRepository: ReportRepositoryInterface,
    reportFileRepository: ReportFileRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    pdfService: PDFService,
    frontRoute: FrontRoute,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents,
    reportWithDataOrchestrator: ReportWithDataOrchestrator
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def createReport: Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    for {
      draftReport <- request.parseBody[ReportDraft]()
      createdReport <- reportOrchestrator.validateAndCreateReport(draftReport).recover {
        case err: SpammerEmailBlocked =>
          logger.warn(err.details)
          reportOrchestrator.createFakeReportForBlacklistedUser(draftReport)
        case err => throw err
      }
    } yield Ok(Json.toJson(createdReport))

  }

  def updateReportCompany(uuid: UUID): Action[JsValue] =
    SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>
      for {
        reportCompany <- request.parseBody[ReportCompany]()
        result <- reportOrchestrator
          .updateReportCompany(uuid, reportCompany, request.identity.id)
          .map {
            case Some(report) => Ok(Json.toJson(report))
            case None         => NotFound
          }
      } yield result
    }

  def updateReportConsumer(uuid: UUID): Action[JsValue] =
    SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>
      for {
        reportConsumer <- request.parseBody[ReportConsumerUpdate]()
        result <- reportOrchestrator
          .updateReportConsumer(uuid, reportConsumer, request.identity.id)
          .map {
            case Some(report) => Ok(Json.toJson(report))
            case None         => NotFound
          }
      } yield result
    }

  def reportResponse(uuid: UUID): Action[JsValue] = SecuredAction(WithRole(UserRole.Professionnel)).async(parse.json) {
    implicit request =>
      logger.debug(s"reportResponse ${uuid}")
      for {
        reportResponse <- request.parseBody[ReportResponse]()
        visibleReport <- reportOrchestrator.getVisibleReportForUser(uuid, request.identity)
        updatedReport <- visibleReport
          .map(reportOrchestrator.handleReportResponse(_, reportResponse, request.identity))
          .sequence
      } yield updatedReport
        .map(r => Ok(Json.toJson(r)))
        .getOrElse(NotFound)

  }

  def createReportAction(uuid: UUID): Action[JsValue] =
    SecuredAction(WithPermission(UserPermission.createReportAction)).async(parse.json) { implicit request =>
      for {
        reportAction <- request.parseBody[ReportAction]()
        report <- reportRepository.get(uuid)
        newEvent <-
          report
            .filter(_ => actionsForUserRole(request.identity.userRole).contains(reportAction.actionType))
            .map(reportOrchestrator.handleReportAction(_, reportAction, request.identity))
            .sequence
      } yield newEvent
        .map(e => Ok(Json.toJson(e)))
        .getOrElse(NotFound)

    }
  def getReport(uuid: UUID) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    for {
      visibleReport <- reportOrchestrator.getVisibleReportForUser(uuid, request.identity)
      viewedReport <- visibleReport
        .map(r => reportOrchestrator.handleReportView(r, request.identity).map(Some(_)))
        .getOrElse(Future(None))
      reportFiles <- viewedReport
        .map(r => reportFileRepository.retrieveReportFiles(r.id))
        .getOrElse(Future(List.empty))
    } yield viewedReport
      .map(report => Ok(Json.toJson(ReportWithFiles(report, reportFiles))))
      .getOrElse(NotFound)
  }

  def reportsAsPDF() = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    val reportFutures = new QueryStringMapper(request.queryString)
      .seq("ids")
      .map(extractUUID)
      .map(reportId => reportWithDataOrchestrator.getReportFull(reportId, request.identity))
    Future
      .sequence(reportFutures)
      .map(_.flatten)
      .map(
        _.map { reportData =>
          val lang = Lang(reportData.report.lang.getOrElse(Locale.FRENCH))
          val messagesProvider: MessagesProvider = MessagesImpl(lang, controllerComponents.messagesApi)
          views.html.pdfs
            .report(
              reportData.report,
              reportData.maybeCompany,
              reportData.events,
              reportData.responseOption,
              reportData.consumerReviewOption,
              reportData.companyEvents,
              reportData.files
            )(frontRoute = frontRoute, None, messagesProvider)
        }
      )
      .map(pdfService.createPdfSource)
      .map(pdfSource =>
        Ok.chunked(
          content = pdfSource,
          inline = true,
          fileName = Some(s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.pdf")
        )
      )
  }

  def cloudWord(companyId: UUID) = UserAwareAction.async(parse.empty) { _ =>
    reportOrchestrator
      .getCloudWord(companyId)
      .map(cloudword => Ok(Json.toJson(cloudword)))
  }

  def deleteReport(uuid: UUID): Action[AnyContent] = SecuredAction(WithPermission(UserPermission.deleteReport)).async {
    reportOrchestrator.deleteReport(uuid).map(if (_) NoContent else NotFound)
  }

  def generateConsumerReportEmailAsPDF(uuid: UUID) =
    SecuredAction(WithPermission(UserPermission.generateConsumerReportEmailAsPDF)).async { implicit request =>
      for {
        maybeReport <- reportRepository.get(uuid)
        company <- maybeReport.flatMap(_.companyId).flatTraverse(r => companyRepository.get(r))
        files <- reportFileRepository.retrieveReportFiles(uuid)
        events <- eventsOrchestrator.getReportsEvents(
          reportId = uuid,
          eventType = None,
          userRole = request.identity.userRole
        )
        proResponseEvent = events.find(_.data.action == REPORT_PRO_RESPONSE)
        source = maybeReport
          .map { report =>
            val lang = Lang(report.lang.getOrElse(Locale.FRENCH))
            val messagesProvider: MessagesProvider = MessagesImpl(lang, controllerComponents.messagesApi)
            val notificationHtml =
              views.html.mails.consumer.reportAcknowledgment(report, company, files, isPDF = true)(
                frontRoute,
                messagesProvider
              )
            val proResponseHtml = views.html.pdfs.proResponse(proResponseEvent.map(_.data))
            Seq(notificationHtml, proResponseHtml)
          }
          .map(pdfService.createPdfSource)
      } yield source match {
        case Some(pdfSource) =>
          Ok.chunked(
            content = pdfSource,
            inline = true,
            fileName = Some(s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.pdf")
          )
        case None => NotFound
      }
    }
}
