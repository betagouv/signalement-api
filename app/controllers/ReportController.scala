package controllers

import com.mohiva.play.silhouette.api.Silhouette
import config.AppConfigLoader
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import repositories._
import services.PDFService
import services.S3Service
import utils.Constants.ActionEvent._
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.Constants
import utils.FrontRoute
import utils.QueryStringMapper
import utils.SIRET
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission
import utils.silhouette.auth.WithRole

import java.nio.file.Paths
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ReportController @Inject() (
    reportOrchestrator: ReportOrchestrator,
    companyRepository: CompanyRepository,
    reportRepository: ReportRepository,
    eventRepository: EventRepository,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    s3Service: S3Service,
    pdfService: PDFService,
    frontRoute: FrontRoute,
    val silhouette: Silhouette[AuthEnv],
    appConfigLoader: AppConfigLoader
)(implicit val ec: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def createReport = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[DraftReport]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        report => reportOrchestrator.newReport(report).map(_.map(r => Ok(Json.toJson(r))).getOrElse(Forbidden))
      )
  }

  def updateReportCompany(uuid: String) = SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) {
    implicit request =>
      request.body
        .validate[ReportCompany]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          reportCompany =>
            reportOrchestrator.updateReportCompany(UUID.fromString(uuid), reportCompany, request.identity.id).map {
              case Some(report) => Ok(Json.toJson(report))
              case None         => NotFound
            }
        )
  }

  def updateReportConsumer(uuid: String) =
    SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>
      request.body
        .validate[ReportConsumer]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          reportConsumer =>
            reportOrchestrator.updateReportConsumer(UUID.fromString(uuid), reportConsumer, request.identity.id).map {
              case Some(report) => Ok(Json.toJson(report))
              case None         => NotFound
            }
        )
    }

  def reportResponse(uuid: String) = SecuredAction(WithRole(UserRole.Professionnel)).async(parse.json) {
    implicit request =>
      logger.debug(s"reportResponse ${uuid}")
      request.body
        .validate[ReportResponse]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          reportResponse =>
            for {
              visibleReport <- getVisibleReportForUser(UUID.fromString(uuid), request.identity)
              updatedReport <-
                visibleReport
                  .map(reportOrchestrator.handleReportResponse(_, reportResponse, request.identity).map(Some(_)))
                  .getOrElse(Future(None))
            } yield updatedReport
              .map(r => Ok(Json.toJson(r)))
              .getOrElse(NotFound)
        )
  }

  def createReportAction(uuid: String) =
    SecuredAction(WithPermission(UserPermission.createReportAction)).async(parse.json) { implicit request =>
      request.body
        .validate[ReportAction]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          reportAction =>
            for {
              report <- reportRepository.getReport(UUID.fromString(uuid))
              newEvent <-
                report
                  .filter(_ => actionsForUserRole(request.identity.userRole).contains(reportAction.actionType))
                  .map(reportOrchestrator.handleReportAction(_, reportAction, request.identity).map(Some(_)))
                  .getOrElse(Future(None))
            } yield newEvent
              .map(e => Ok(Json.toJson(e)))
              .getOrElse(NotFound)
        )
    }

  def reviewOnReportResponse(uuid: String) = UnsecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[ReviewOnReportResponse]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        review =>
          for {
            events <- eventRepository.getEvents(UUID.fromString(uuid), EventFilter())
            result <-
              if (!events.exists(_.action == ActionEvent.REPORT_PRO_RESPONSE)) {
                Future(Forbidden)
              } else if (events.exists(_.action == ActionEvent.REPORT_REVIEW_ON_RESPONSE)) {
                Future(Conflict)
              } else {
                reportOrchestrator.handleReviewOnReportResponse(UUID.fromString(uuid), review).map(_ => Ok)
              }
          } yield result
      )
  }

  def uploadReportFile = UnsecuredAction.async(parse.multipartFormData) { request =>
    request.body
      .file("reportFile")
      .filter(f =>
        appConfigLoader.get.upload.allowedExtensions
          .contains(f.filename.toLowerCase.toString.split("\\.").last)
      )
      .map { reportFile =>
        val filename = Paths.get(reportFile.filename).getFileName
        val tmpFile =
          new java.io.File(s"${appConfigLoader.get.tmpDirectory}/${UUID.randomUUID}_${filename}")
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

  def downloadReportFile(uuid: String, filename: String) = UnsecuredAction.async { _ =>
    reportRepository
      .getFile(UUID.fromString(uuid))
      .map {
        case Some(file) if file.avOutput.isEmpty =>
          Conflict("Analyse antivirus en cours, veuillez réessayer d'ici 30 secondes") // HTTP 409
        case Some(file) if file.filename == filename && file.avOutput.isDefined =>
          Redirect(s3Service.getSignedUrl(file.storageFilename))
        case _ => NotFound
      }
  }

  def deleteReportFile(id: String, filename: String) = UserAwareAction.async { implicit request =>
    val uuid = UUID.fromString(id)
    reportRepository
      .getFile(uuid)
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

  def getReport(uuid: String) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
//    reminderTask.runTask(LocalDate.now.atStartOfDay())
    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(_) =>
        for {
          visibleReport <- getVisibleReportForUser(UUID.fromString(uuid), request.identity)
          viewedReport <- visibleReport
            .map(r => reportOrchestrator.handleReportView(r, request.identity).map(Some(_)))
            .getOrElse(Future(None))
          reportFiles <- viewedReport.map(r => reportRepository.retrieveReportFiles(r.id)).getOrElse(Future(List.empty))
        } yield viewedReport
          .map(report => Ok(Json.toJson(ReportWithFiles(report, reportFiles))))
          .getOrElse(NotFound)
    }
  }

  def reportAsPDF(uuid: String) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) =>
        for {
          visibleReport <- getVisibleReportForUser(id, request.identity)
          events <- eventRepository.getEventsWithUsers(id, EventFilter())
          companyEvents <- visibleReport
            .flatMap(_.companyId)
            .map(companyId => eventRepository.getCompanyEventsWithUsers(companyId, EventFilter()))
            .getOrElse(Future(List.empty))
          reportFiles <- reportRepository.retrieveReportFiles(id)
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
  }

  def deleteReport(uuid: String) = SecuredAction(WithPermission(UserPermission.deleteReport)).async {
    Try(UUID.fromString(uuid)) match {
      case Failure(_)  => Future.successful(PreconditionFailed)
      case Success(id) => reportOrchestrator.deleteReport(id).map(if (_) NoContent else NotFound)
    }
  }

  def getEvents(reportId: String, eventType: Option[String]) =
    SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
      val filter = eventType match {
        case Some(_) => EventFilter(eventType = Some(EventType.fromValue(eventType.get)))
        case None    => EventFilter()
      }

      Try(UUID.fromString(reportId)) match {
        case Failure(_) => Future.successful(PreconditionFailed)
        case Success(id) =>
          for {
            report <- reportRepository.getReport(id)
            events <- eventRepository.getEventsWithUsers(id, filter)
          } yield report match {
            case Some(_) =>
              Ok(
                Json.toJson(
                  events
                    .filter(event =>
                      request.identity.userRole match {
                        case UserRole.Professionnel =>
                          List(REPORT_PRO_RESPONSE, REPORT_READING_BY_PRO) contains event._1.action
                        case _ => true
                      }
                    )
                    .map { case (event, user) =>
                      Json.obj(
                        "data" -> event,
                        "user" -> user.map(u =>
                          Json.obj(
                            "firstName" -> u.firstName,
                            "lastName" -> u.lastName,
                            "role" -> u.userRole.entryName
                          )
                        )
                      )
                    }
                )
              )
            case None => NotFound
          }
      }
    }

  def getCompanyEvents(siret: String, eventType: Option[String]) =
    SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
      val filter = eventType match {
        case Some(_) => EventFilter(eventType = Some(EventType.fromValue(eventType.get)))
        case None    => EventFilter()
      }
      for {
        company <- companyRepository.findBySiret(SIRET(siret))
        events <- company
          .map(_.id)
          .map(id => eventRepository.getCompanyEventsWithUsers(id, filter).map(Some(_)))
          .getOrElse(Future(None))
      } yield company match {
        case Some(_) =>
          Ok(
            Json.toJson(
              events.get
                .filter(event =>
                  request.identity.userRole match {
                    case UserRole.Professionnel =>
                      List(REPORT_PRO_RESPONSE, REPORT_READING_BY_PRO) contains event._1.action
                    case _ => true
                  }
                )
                .map { case (event, user) =>
                  Json.obj(
                    "data" -> event,
                    "user" -> user.map(u =>
                      Json.obj(
                        "firstName" -> u.firstName,
                        "lastName" -> u.lastName,
                        "role" -> u.userRole.entryName
                      )
                    )
                  )
                }
            )
          )
        case None => NotFound
      }
    }

  private def getVisibleReportForUser(reportId: UUID, user: User): Future[Option[Report]] =
    for {
      report <- reportRepository.getReport(reportId)
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

  def countByDepartments() = SecuredAction(WithRole(UserRole.Admin)).async { implicit request =>
    val mapper = new QueryStringMapper(request.queryString)
    val start = mapper.localDate("start")
    val end = mapper.localDate("end")
    reportOrchestrator.countByDepartments(start, end).map(res => Ok(Json.toJson(res)))
  }
}
