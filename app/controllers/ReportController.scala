package controllers

import java.net.URI
import java.util.UUID

import akka.stream.alpakka.s3.MultipartUploadResult
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models._
import orchestrators.ReportOrchestrator
import play.api.libs.json.{JsError, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.{Configuration, Logger}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import repositories._
import services.{MailerService, S3Service, PDFService}
import utils.Constants.ActionEvent._
import utils.Constants.{ActionEvent, EventType}
import utils.SIRET
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission, WithRole}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ReportController @Inject()(reportOrchestrator: ReportOrchestrator,
                                 companyRepository: CompanyRepository,
                                 reportRepository: ReportRepository,
                                 eventRepository: EventRepository,
                                 userRepository: UserRepository,
                                 mailerService: MailerService,
                                 s3Service: S3Service,
                                 pdfService: PDFService,
                                 val silhouette: Silhouette[AuthEnv],
                                 val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                 configuration: Configuration)
                                (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  val BucketName = configuration.get[String]("play.buckets.report")
  implicit val websiteUrl = configuration.get[URI]("play.application.url")

  private def getProLevel(user: User, report: Option[Report]) =
    report
      .filter(_.status.getValueWithUserRole(user.userRole).isDefined)
      .flatMap(_.companyId).map(companyRepository.getUserLevel(_, user))
      .getOrElse(Future(AccessLevel.NONE))

  def createReport = UnsecuredAction.async(parse.json) { implicit request =>
    request.body.validate[DraftReport].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      report => reportOrchestrator.newReport(report).map(report => Ok(Json.toJson(report)))
    )
  }

  def updateReportCompany(uuid: String) = SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>
    request.body.validate[ReportCompany].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      reportCompany => reportOrchestrator.updateReportCompany(UUID.fromString(uuid), reportCompany, request.identity.id).map{
            case Some(report) => Ok(Json.toJson(report))
            case None => NotFound
          }
    )
  }

  def updateReportConsumer(uuid: String) = SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>
    request.body.validate[ReportConsumer].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      reportConsumer => reportOrchestrator.updateReportConsumer(UUID.fromString(uuid), reportConsumer, request.identity.id).map{
            case Some(_) => Ok
            case None => NotFound
          }
    )
  }

  def reportResponse(uuid: String) = SecuredAction(WithRole(UserRoles.Pro)).async(parse.json) { implicit request =>
    logger.debug(s"reportResponse ${uuid}")
    request.body.validate[ReportResponse].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      reportResponse => {
        for {
          report <- reportRepository.getReport(UUID.fromString(uuid))
          level <-  getProLevel(request.identity, report)
          updatedReport <- report.filter(_ => level != AccessLevel.NONE)
            .map(reportOrchestrator.handleReportResponse(_, reportResponse, request.identity).map(Some(_))).getOrElse(Future(None))
        } yield updatedReport
          .map(r => Ok(Json.toJson(r)))
          .getOrElse(NotFound)
        }
      )
  }

  def createReportAction(uuid: String) = SecuredAction(WithPermission(UserPermission.createReportAction)).async(parse.json) { implicit request =>
    request.body.validate[ReportAction].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      reportAction =>
        for {
          report <- reportRepository.getReport(UUID.fromString(uuid))
          newEvent <- report.filter(_ => actionsForUserRole(request.identity.userRole).contains(reportAction.actionType))
            .map(reportOrchestrator.handleReportAction(_, reportAction, request.identity).map(Some(_))).getOrElse(Future(None))
        } yield newEvent
          .map(e => Ok(Json.toJson(e)))
          .getOrElse(NotFound)
    )
  }

  def reviewOnReportResponse(uuid: String) = UnsecuredAction.async(parse.json) { implicit request =>
    request.body.validate[ReviewOnReportResponse].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      review => for {
          events <- eventRepository.getEvents(None, Some(UUID.fromString(uuid)), EventFilter())
          result <- if (!events.exists(_.action == ActionEvent.REPONSE_PRO_SIGNALEMENT)) {
            Future(Forbidden)
          } else if (events.exists(_.action == ActionEvent.REVIEW_ON_REPORT_RESPONSE)) {
            Future(Conflict)
          } else {
            reportOrchestrator.handleReviewOnReportResponse(UUID.fromString(uuid), review).map(_ => Ok)
          }
        } yield result
    )
  }

  def uploadReportFile = UnsecuredAction.async(parse.multipartFormData(handleFilePartAwsUploadResult)) { request =>
    val maybeUploadResult =
      request.body.file("reportFile").map {
        case FilePart(key, filename, contentType, multipartUploadResult, _, _) =>
          (multipartUploadResult, filename)
      }

    maybeUploadResult.fold(Future(InternalServerError("Echec de l'upload"))) {
      maybeUploadResult =>
        reportOrchestrator
        .addReportFile(
          maybeUploadResult._2,
          maybeUploadResult._1.key,
          request.body.dataParts.get("reportFileOrigin").map(o => ReportFileOrigin(o.head)).getOrElse(ReportFileOrigin.CONSUMER)
        )
        .map(file => Ok(Json.toJson(file)))
      }
  }

  private def handleFilePartAwsUploadResult: Multipart.FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, filename, contentType, dispositionType) =>
      val accumulator = Accumulator(s3Service.upload(BucketName, s"${UUID.randomUUID}_${filename}"))

      accumulator map { multipartUploadResult =>
        FilePart(partName, filename, contentType, multipartUploadResult)
      }
  }

  def downloadReportFile(uuid: String, filename: String) = UnsecuredAction.async { implicit request =>
    reportRepository.getFile(UUID.fromString(uuid)).map(_ match {
      case Some(file) if file.filename == filename => Redirect(s3Service.getSignedUrl(BucketName, file.storageFilename))
      case _ => NotFound
    })
  }

  def deleteReportFile(id: String, filename: String) = UserAwareAction.async { implicit request =>
    val uuid = UUID.fromString(id)
    reportRepository.getFile(uuid).flatMap(_ match {
      case Some(file) if file.filename == filename =>
        (file.reportId, request.identity) match {
          case (None, _) =>
            reportOrchestrator.removeReportFile(uuid).map(_ => NoContent)
          case (Some(reportId), Some(identity)) if identity.userRole.permissions.contains(UserPermission.deleteFile) =>
            reportOrchestrator.removeReportFile(uuid).map(_ => NoContent)
          case (_, _) => Future(Forbidden)
        }
      case _ => Future(NotFound)
    })
  }

  def getReport(uuid: String) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => for {
        report        <- reportRepository.getReport(id)
        proLevel      <- getProLevel(request.identity, report)
        updatedReport <- report
                          .filter(_ =>
                                  request.identity.userRole == UserRoles.DGCCRF
                              ||  request.identity.userRole == UserRoles.Admin
                              ||  proLevel != AccessLevel.NONE)
                          match {
                            case Some(r) => reportOrchestrator.handleReportView(r, request.identity).map(Some(_))
                            case _ => Future(None)
                          }
        reportFiles <- report.map(r => reportRepository.retrieveReportFiles(r.id)).getOrElse(Future(List.empty))
      } yield updatedReport
              .map(report => Ok(Json.toJson(ReportWithFiles(report, reportFiles))))
              .getOrElse(NotFound)
    }
  }

  def reportAsPDF(uuid: String) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => for {
        report        <- reportRepository.getReport(id)
        events        <- eventRepository.getEventsWithUsers(None, Some(id), EventFilter())
        reportFiles   <- reportRepository.retrieveReportFiles(id)
        proLevel      <- getProLevel(request.identity, report)
      } yield report
              .filter(_ =>
                              request.identity.userRole == UserRoles.DGCCRF
                          ||  request.identity.userRole == UserRoles.Admin
                          ||  proLevel != AccessLevel.NONE)
              .map(report =>
                  pdfService.Ok(
                    List(views.html.pdfs.report(report, events, reportFiles))
                  )
              )
              .getOrElse(NotFound)
    }
  }

  def deleteReport(uuid: String) = SecuredAction(WithPermission(UserPermission.deleteReport)).async {
    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => reportOrchestrator.deleteReport(id).map(if (_) NoContent else NotFound)
    }
  }

  def getReportCountBySiret(siret: String) = silhouetteAPIKey.SecuredAction.async {
    reportRepository.count(Some(SIRET(siret))).flatMap(count => Future(Ok(Json.obj("siret" -> siret, "count" -> count))))
  }

  def getEvents(uuid: String, eventType: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    val filter = eventType match {
      case Some(_) => EventFilter(eventType = Some(EventType.fromValue(eventType.get)))
      case None => EventFilter()
    }

    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => {
        for {
          report <- reportRepository.getReport(id)
          events <- eventRepository.getEventsWithUsers(report.flatMap(_.companyId), Some(id), filter)
        } yield {
          report match {
            case Some(_) => Ok(Json.toJson(
              events.filter(event =>
                request.identity.userRole match {
                  case UserRoles.Pro => List(REPONSE_PRO_SIGNALEMENT, ENVOI_SIGNALEMENT) contains event._1.action
                  case _ => true
                }
              )
              .map { case (event, user) => Json.obj(
                "data" -> event,
                "user"  -> user.map(u => Json.obj(
                  "firstName" -> u.firstName,
                  "lastName"  -> u.lastName,
                  "role"      -> u.userRole.name
                ))
              )}
            ))
            case None => NotFound
          }
        }
      }}
  }

  def getNbReportsGroupByCompany(offset: Option[Long], limit: Option[Int]) = SecuredAction.async { implicit request =>
    implicit val paginatedReportWriter = PaginatedResult.paginatedCompanyWithNbReportsWriter

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    val offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    val limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    reportRepository.getNbReportsGroupByCompany(offsetNormalized, limitNormalized).flatMap( paginatedReports => {
      Future.successful(Ok(Json.toJson(paginatedReports)))
    })

  }


}
