package controllers

import cats.implicits.toTraverseOps
import com.mohiva.play.silhouette.api.Silhouette
import config.SignalConsoConfiguration
import controllers.error.AppError.SpammerEmailBlocked
import models._
import models.report.Report
import models.report.ReportAction
import models.report.ReportCompany
import models.report.ReportConsumerUpdate
import models.report.ReportDraft
import models.report.ReportFileOrigin
import models.report.ReportResponse
import models.report.ReportWithFiles
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.Files
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.MultipartFormData
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportfile.ReportFileRepositoryInterface
import services.PDFService
import utils.Constants.ActionEvent._
import utils.Constants
import utils.FrontRoute
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission
import utils.silhouette.auth.WithRole

import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportController(
    reportOrchestrator: ReportOrchestrator,
    reportRepository: ReportRepositoryInterface,
    reportFileRepository: ReportFileRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    pdfService: PDFService,
    frontRoute: FrontRoute,
    val silhouette: Silhouette[AuthEnv],
    signalConsoConfiguration: SignalConsoConfiguration,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def createReport: Action[JsValue] = UnsecuredAction.async(parse.json) { implicit request =>
    val errorOrReport = for {
      draftReport <- request.parseBody[ReportDraft]()
      createdReport <- reportOrchestrator.validateAndCreateReport(draftReport)
    } yield Ok(Json.toJson(createdReport))

    errorOrReport.recoverWith {
      case err: SpammerEmailBlocked =>
        logger.warn(err.details)
        Future.successful(Ok)
      case err => Future.failed(err)
    }
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
        visibleReport <- getVisibleReportForUser(uuid, request.identity)
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

  def uploadReportFile: Action[MultipartFormData[Files.TemporaryFile]] =
    UnsecuredAction.async(parse.multipartFormData) { request =>
      request.body
        .file("reportFile")
        .filter(f =>
          signalConsoConfiguration.upload.allowedExtensions
            .contains(f.filename.toLowerCase.toString.split("\\.").last)
        )
        .map { reportFile =>
          val filename = Paths.get(reportFile.filename).getFileName
          val tmpFile =
            new java.io.File(s"${signalConsoConfiguration.tmpDirectory}/${UUID.randomUUID}_${filename}")
          reportFile.ref.copyTo(tmpFile)
          reportOrchestrator
            .saveReportFile(
              filename.toString,
              tmpFile,
              request.body.dataParts
                .get("reportFileOrigin")
                .map(o => ReportFileOrigin(o.head))
                .getOrElse(ReportFileOrigin.CONSUMER)
            )
            .map(file => Ok(Json.toJson(file)))
        }
        .getOrElse(Future(InternalServerError("Echec de l'upload")))
    }

  def downloadReportFile(uuid: UUID, filename: String) = UnsecuredAction.async { _ =>
    reportOrchestrator
      .downloadReportAttachment(uuid, filename)
      .map(signedUrl => Redirect(signedUrl))

  }

  def deleteReportFile(uuid: UUID, filename: String) = UserAwareAction.async { implicit request =>
    reportFileRepository
      .get(uuid)
      .flatMap {
        case Some(file) if file.filename == filename =>
          (file.reportId, request.identity) match {
            case (None, _) =>
              reportOrchestrator.removeReportFile(uuid).map(_ => NoContent)
            case (Some(_), Some(identity)) if identity.userRole.permissions.contains(UserPermission.deleteFile) =>
              reportOrchestrator.removeReportFile(uuid).map(_ => NoContent)
            case (_, _) => Future(Forbidden)
          }
        case _ => Future(NotFound)
      }
  }

  def getReport(uuid: UUID) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    for {
      visibleReport <- getVisibleReportForUser(uuid, request.identity)
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

  def reportAsPDF(uuid: UUID) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    for {
      visibleReport <- getVisibleReportForUser(uuid, request.identity)
      events <- eventRepository.getEventsWithUsers(uuid, EventFilter())
      companyEvents <- visibleReport
        .flatMap(_.companyId)
        .map(companyId => eventRepository.getCompanyEventsWithUsers(companyId, EventFilter()))
        .getOrElse(Future(List.empty))
      reportFiles <- reportFileRepository.retrieveReportFiles(uuid)
    } yield {
      val responseOption = events
        .map(_._1)
        .find(_.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
        .map(_.details)
        .map(_.as[ReportResponse])

      visibleReport
        .map(report =>
          pdfService.Ok(
            List(
              views.html.pdfs.report(report, events, responseOption, companyEvents, reportFiles)(frontRoute =
                frontRoute
              )
            )
          )
        )
        .getOrElse(NotFound)
    }

  }

  def deleteReport(uuid: UUID): Action[AnyContent] = SecuredAction(WithPermission(UserPermission.deleteReport)).async {
    reportOrchestrator.deleteReport(uuid).map(if (_) NoContent else NotFound)
  }

  private def getVisibleReportForUser(reportId: UUID, user: User): Future[Option[Report]] =
    for {
      report <- reportRepository.get(reportId)
      visibleReport <-
        if (Seq(UserRole.DGCCRF, UserRole.Admin).contains(user.userRole))
          Future(report)
        else {
          companiesVisibilityOrchestrator
            .fetchVisibleCompanies(user)
            .map(_.map(v => Some(v.company.siret)))
            .map { visibleSirets =>
              report.filter(r => visibleSirets.contains(r.companySiret))
            }
        }
    } yield visibleReport

}
