package controllers

import authentication.Authenticator
import authentication.actions.UserAction.WithPermission
import authentication.actions.UserAction.WithRole
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import controllers.error.AppError
import controllers.error.AppError.SpammerEmailBlocked
import models._
import models.report._
import models.report.delete.ReportAdminAction
import orchestrators._
import play.api.Logger
import play.api.i18n.Lang
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportfile.ReportFileRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.PDFService
import utils.Constants.ActionEvent._
import utils.FrontRoute
import utils.QueryStringMapper

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportController(
    reportOrchestrator: ReportOrchestrator,
    reportAssignmentOrchestrator: ReportAssignmentOrchestrator,
    reportAdminActionOrchestrator: ReportAdminActionOrchestrator,
    eventsOrchestrator: EventsOrchestratorInterface,
    reportRepository: ReportRepositoryInterface,
    userRepository: UserRepositoryInterface,
    reportFileRepository: ReportFileRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    pdfService: PDFService,
    frontRoute: FrontRoute,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents,
    reportWithDataOrchestrator: ReportWithDataOrchestrator,
    massImportService: ReportZipExportService
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def createReport: Action[JsValue] = IpRateLimitedAction2.async(parse.json) { implicit request =>
    implicit val userRole: Option[UserRole] = None
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
    SecuredAction.andThen(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>
      implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
      for {
        reportCompany <- request.parseBody[ReportCompany]()
        result <- reportOrchestrator
          .updateReportCompanyIfRecent(uuid, reportCompany, request.identity.id)
          .map { report =>
            Ok(Json.toJson(report))
          }
      } yield result
    }

  def updateReportCountry(uuid: UUID, countryCode: String) =
    SecuredAction.andThen(WithPermission(UserPermission.updateReport)).async { implicit request =>
      implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
      reportOrchestrator
        .updateReportCountry(uuid, countryCode, request.identity.id)
        .map {
          case Some(report) => Ok(Json.toJson(report))
          case None         => NotFound
        }
    }

  def updateReportConsumer(uuid: UUID): Action[JsValue] =
    SecuredAction.andThen(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>
      implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
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

  def reportResponse(uuid: UUID): Action[JsValue] =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).async(parse.json) { implicit request =>
      implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
      logger.debug(s"reportResponse ${uuid}")
      for {
        reportResponse            <- request.parseBody[IncomingReportResponse]()
        visibleReportWithMetadata <- reportOrchestrator.getVisibleReportForUser(uuid, request.identity)
        visibleReport = visibleReportWithMetadata.map(_.report)
        updatedReport <- visibleReport
          .map(reportOrchestrator.handleReportResponse(_, reportResponse, request.identity))
          .sequence
      } yield updatedReport
        .map(r => Ok(Json.toJson(r)))
        .getOrElse(NotFound)

    }

  def createReportAction(uuid: UUID): Action[JsValue] =
    SecuredAction.andThen(WithPermission(UserPermission.createReportAction)).async(parse.json) { implicit request =>
      for {
        reportAction       <- request.parseBody[ReportAction]()
        reportWithMetadata <- reportRepository.getFor(Some(request.identity.userRole), uuid)
        report = reportWithMetadata.map(_.report)
        newEvent <-
          report
            .filter(_ => actionsForUserRole(request.identity.userRole).contains(reportAction.actionType))
            .map(reportOrchestrator.handleReportAction(_, reportAction, request.identity))
            .sequence
      } yield newEvent
        .map(e => Ok(Json.toJson(e)))
        .getOrElse(NotFound)

    }
  def getReport(uuid: UUID) =
    SecuredAction.andThen(WithPermission(UserPermission.listReports)).async { implicit request =>
      implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
      for {
        maybeReportWithMetadata <- reportOrchestrator.getVisibleReportForUser(uuid, request.identity)
        viewedReportWithMetadata <- maybeReportWithMetadata
          .map(r => reportOrchestrator.handleReportView(r, request.identity).map(Some(_)))
          .getOrElse(Future.successful(None))
        reportFiles <- viewedReportWithMetadata
          .map(r => reportFileRepository.retrieveReportFiles(r.report.id))
          .getOrElse(Future.successful(List.empty))
        assignedUserId = viewedReportWithMetadata.flatMap(_.metadata.flatMap(_.assignedUserId))
        maybeAssignedUser <- assignedUserId
          .map(userId => userRepository.get(userId))
          .getOrElse(Future.successful(None))
        maybeAssignedMinimalUser = maybeAssignedUser.map(MinimalUser.fromUser)
      } yield viewedReportWithMetadata
        .map(r =>
          Ok(
            Json.toJson(
              ReportWithFilesAndAssignedUser(
                r.report,
                r.metadata,
                maybeAssignedMinimalUser,
                reportFiles.map(ReportFileApi.build(_))
              )
            )
          )
        )
        .getOrElse(NotFound)
    }

  def reportsAsPDF() = SecuredAction.andThen(WithPermission(UserPermission.listReports)).async { implicit request =>
    val reportFutures = new QueryStringMapper(request.queryString)
      .seq("ids")
      .map(extractUUID)
      .map(reportId => reportWithDataOrchestrator.getReportFull(reportId, request.identity))
    Future
      .sequence(reportFutures)
      .map(_.flatten)
      .map(
        _.map { reportData =>
          val lang                               = Lang(reportData.report.lang.getOrElse(Locale.FRENCH))
          val messagesProvider: MessagesProvider = MessagesImpl(lang, controllerComponents.messagesApi)
          views.html.pdfs
            .report(
              reportData.report,
              reportData.maybeCompany,
              reportData.events,
              reportData.responseOption,
              reportData.consumerReviewOption,
              reportData.engagementReviewOption,
              reportData.companyEvents,
              reportData.files
            )(frontRoute = frontRoute, None, messagesProvider)
        }
      )
      .map(pdfService.createPdfSource)
      .map(pdfSource =>
        Ok.chunked(
          content = pdfSource,
          inline = false,
          fileName = Some(s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.pdf")
        )
      )
  }

  def reportAsZip(reportId: UUID) =
    SecuredAction.andThen(WithPermission(UserPermission.listReports)).async(parse.empty) { implicit request =>
      reportWithDataOrchestrator
        .getReportFull(reportId, request.identity)
        .flatMap(_.liftTo[Future](AppError.ReportNotFound(reportId)))
        .flatMap(reportData => massImportService.reportSummaryWithAttachmentsZip(reportData))
        .map(pdfSource =>
          Ok.chunked(
            content = pdfSource,
            inline = false,
            fileName = Some(s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.zip")
          )
        )
    }

  def cloudWord(companyId: UUID) = IpRateLimitedAction2.async(parse.empty) { _ =>
    reportOrchestrator
      .getCloudWord(companyId)
      .map(cloudword => Ok(Json.toJson(cloudword)))
  }

  def deleteReport(uuid: UUID) =
    SecuredAction.andThen(WithPermission(UserPermission.deleteReport)).async(parse.json) { request =>
      for {
        reportDeletionReason <- request.parseBody[ReportAdminAction]()
        _ <- reportAdminActionOrchestrator.reportDeletion(
          uuid,
          reportDeletionReason,
          request.identity
        )
      } yield NoContent
    }

  def deleteSpamReport() =
    SecuredAction.andThen(WithPermission(UserPermission.deleteReport)).async(parse.json) { request =>
      for {
        reportsIds <- request.parseBody[List[UUID]]()
        deleted <- reportAdminActionOrchestrator.deleteSpammedReport(
          reportsIds,
          request.identity
        )
      } yield Ok(Json.toJson(deleted))
    }

  def reopenReport(uuid: UUID) =
    SecuredAction.andThen(WithPermission(UserPermission.deleteReport)).async(parse.empty) { request =>
      for {
        _ <- reportAdminActionOrchestrator.reportReOpening(
          uuid,
          request.identity
        )
      } yield NoContent
    }

  def updateReportAssignedUser(uuid: UUID, userId: UUID) =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
      for {
        updatedReportWithMetadata <- reportAssignmentOrchestrator
          .assignReportToUser(reportId = uuid, assigningUser = request.identity, newAssignedUserId = userId)
      } yield Ok(Json.toJson(updatedReportWithMetadata))
    }

  def generateConsumerReportEmailAsPDF(uuid: UUID) =
    SecuredAction.andThen(WithPermission(UserPermission.generateConsumerReportEmailAsPDF)).async { implicit request =>
      for {
        maybeReportWithMetadata <- reportRepository.getFor(Some(request.identity.userRole), uuid)
        maybeReport = maybeReportWithMetadata.map(_.report)
        company <- maybeReport.flatMap(_.companyId).flatTraverse(r => companyRepository.get(r))
        files   <- reportFileRepository.retrieveReportFiles(uuid)
        events <- eventsOrchestrator.getReportsEvents(
          reportId = uuid,
          eventType = None,
          userRole = request.identity.userRole
        )
        proResponseEvent = events.find(_.data.action == REPORT_PRO_RESPONSE)
        source = maybeReport
          .map { report =>
            val lang                                        = Lang(report.lang.getOrElse(Locale.FRENCH))
            implicit val messagesProvider: MessagesProvider = MessagesImpl(lang, controllerComponents.messagesApi)
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
            inline = false,
            fileName = Some(s"${UUID.randomUUID}_${OffsetDateTime.now().toString}.pdf")
          )
        case None => NotFound
      }
    }
}
