package controllers

import java.util.UUID

import akka.stream.alpakka.s3.MultipartUploadResult
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models._
import orchestrators.ReportOrchestrator
import play.api.libs.json.{JsError, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.{Configuration, Environment, Logger}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.EventType
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission, WithRole}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ReportController @Inject()(reportOrchestrator: ReportOrchestrator,
                                 companyAccessRepository: CompanyAccessRepository,
                                 reportRepository: ReportRepository,
                                 eventRepository: EventRepository,
                                 userRepository: UserRepository,
                                 mailerService: MailerService,
                                 s3Service: S3Service,
                                 val silhouette: Silhouette[AuthEnv],
                                 val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                 configuration: Configuration,
                                 environment: Environment)
                                (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  val BucketName = configuration.get[String]("play.buckets.report")

  private def getProLevel(user: User, report: Option[Report]) =
    report
      .filter(_.status.map(_.getValueWithUserRole(user.userRole)).isDefined)
      .flatMap(_.companyId).map(companyAccessRepository.getUserLevel(_, user))
      .getOrElse(Future(AccessLevel.NONE))

  def createEvent(uuid: String) = SecuredAction(WithPermission(UserPermission.createEvent)).async(parse.json) { implicit request =>

    logger.debug("createEvent")

    request.body.validate[Event].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      event => {
        Try(UUID.fromString(uuid)) match {
          case Failure(_) => Future.successful(PreconditionFailed)
          case Success(id) => reportOrchestrator
                                .newEvent(id, event, request.identity)
                                .map(_.map(event => Ok(Json.toJson(event))).getOrElse(NotFound))
        }
      }
    )
  }

  def createReport = UnsecuredAction.async(parse.json) { implicit request =>
    logger.debug("createReport")

    request.body.validate[Report].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      report => reportOrchestrator.newReport(report).map(report => Ok(Json.toJson(report)))
    )
  }

  def updateReport(uuid: String) = SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>

    logger.debug("updateReport")

    request.body.validate[Report].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      report => reportOrchestrator.updateReport(UUID.fromString(uuid), report).map{
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

  def uploadReportFile = UnsecuredAction.async(parse.multipartFormData(handleFilePartAwsUploadResult)) { request =>
    logger.debug("uploadReportFile")

    val maybeUploadResult =
      request.body.file("reportFile").map {
        case FilePart(key, filename, contentType, multipartUploadResult, _, _) =>
          (multipartUploadResult, filename)
      }

    maybeUploadResult.fold(Future(InternalServerError("Echec de l'upload"))) {
      maybeUploadResult =>
        reportOrchestrator
        .addReportFile(
          UUID.fromString(maybeUploadResult._1.key),
          maybeUploadResult._2,
          request.body.dataParts.get("reportFileOrigin").map(o => ReportFileOrigin(o.head)).getOrElse(ReportFileOrigin.CONSUMER)
        )
        .map(file => Ok(Json.toJson(file)))
      }
  }

  private def handleFilePartAwsUploadResult: Multipart.FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, filename, contentType, dispositionType) =>
      val accumulator = Accumulator(s3Service.upload(BucketName, UUID.randomUUID.toString))

      accumulator map { multipartUploadResult =>
        FilePart(partName, filename, contentType, multipartUploadResult)
      }
  }

  def downloadReportFile(uuid: String, filename: String) = UnsecuredAction.async { implicit request =>

    reportRepository.getFile(UUID.fromString(uuid)).flatMap(_ match {
      case Some(file) if file.filename == filename =>
        s3Service.download(BucketName, uuid).flatMap(
          file => {
            val dest: Array[Byte] = new Array[Byte](file.asByteBuffer.capacity())
            file.asByteBuffer.get(dest)
            Future(Ok(dest))
          }
        )
      case _ => Future(NotFound)
    })
  }

  def deleteReportFile(id: String, filename: String) = UserAwareAction.async { implicit request =>
    logger.debug("deleteReportFile")
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

    logger.debug("getReport")

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
      } yield updatedReport
              .map(r => Ok(Json.toJson(r)))
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
    reportRepository.count(Some(siret)).flatMap(count => Future(Ok(Json.obj("siret" -> siret, "count" -> count))))
  }

  def getEvents(uuid: String, eventType: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>

    logger.debug("getEvents")

    val filter = eventType match {
      case Some(_) => EventFilter(eventType = Some(EventType.fromValue(eventType.get)))
      case None => EventFilter()
    }

    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => {
        for {
          report <- reportRepository.getReport(id)
          events <- eventRepository.getEvents(id, filter)
        } yield {
          report match {
            case Some(_) => Ok(Json.toJson(events.filter(event =>
              request.identity.userRole match {
                case UserRoles.Pro => List(REPONSE_PRO_SIGNALEMENT, ENVOI_SIGNALEMENT) contains event.action
                case _ => true
              }
            )))
            case None => NotFound
          }
        }
      }}
  }

  def getNbReportsGroupByCompany(offset: Option[Long], limit: Option[Int]) = SecuredAction.async { implicit request =>
    logger.debug(s"getNbReportsGroupByCompany")

    implicit val paginatedReportWriter = PaginatedResult.paginatedCompanyWithNbReports

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
