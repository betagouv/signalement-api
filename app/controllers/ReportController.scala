package controllers

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, YearMonth}
import java.util.UUID

import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import akka.util.ByteString
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.{Event, File, Report, Statistics}
import play.api.http.HttpEntity
import play.api.libs.json.{JsError, Json}
import play.api.libs.mailer.AttachmentFile
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{ResponseHeader, Result}
import play.api.{Configuration, Environment, Logger}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import repositories.{EventFilter, ReportFilter, ReportRepository, UserRepository}
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.EventType.{CONSO, PRO}
import utils.Constants.StatusConso.{A_RECONTACTER, EN_ATTENTE, StatusConsoValue}
import utils.Constants.StatusPro.{A_TRAITER, NA, StatusProValue}
import utils.Constants.{EventType, StatusConso, StatusPro}
import utils.DateUtils
import utils.silhouette.AuthEnv

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ReportController @Inject()(reportRepository: ReportRepository,
                                 userRepository: UserRepository,
                                 mailerService: MailerService,
                                 s3Service: S3Service,
                                 val silhouette: Silhouette[AuthEnv],
                                 configuration: Configuration,
                                 environment: Environment)
                                (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  val BucketName = configuration.get[String]("play.buckets.report")


  val departmentsAuthorized = List(
    "01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74", // AURA
    "18", "28", "36", "37", "41", "45" // CVDL
  )

  def determineStatusPro(report: Report): Option[StatusProValue] = {

    if (departmentsAuthorized.contains(report.companyPostalCode.get.slice(0, 2))) Some(A_TRAITER) else Some(NA)
  }

  def determineStatusConso(report: Report): Option[StatusConsoValue] = {

    if (departmentsAuthorized.contains(report.companyPostalCode.get.slice(0, 2))) Some(EN_ATTENTE) else Some(A_RECONTACTER)
  }


  def determineStatusPro(event: Event): StatusProValue = (event.action, event.resultAction) match {
    case (A_CONTACTER, _)                      => StatusPro.A_TRAITER
    case (HORS_PERIMETRE, _)                   => StatusPro.NA
    case (CONTACT_TEL, _)                      => StatusPro.TRAITEMENT_EN_COURS
    case (CONTACT_EMAIL, _)                    => StatusPro.TRAITEMENT_EN_COURS
    case (CONTACT_COURRIER, _)                 => StatusPro.TRAITEMENT_EN_COURS
    case (REPONSE_PRO_CONTACT, _)              => StatusPro.A_TRANSFERER_SIGNALEMENT
    case (ENVOI_SIGNALEMENT, _)                => StatusPro.SIGNALEMENT_TRANSMIS
    case (REPONSE_PRO_SIGNALEMENT, Some(true)) => StatusPro.PROMESSE_ACTION
    case (REPONSE_PRO_SIGNALEMENT, _)          => StatusPro.SIGNALEMENT_REFUSE
    case (_, _)                                => StatusPro.NA // cas impossible...

  }

  def determineStatusConso(event: Event, previousStatus: Option[String]): StatusConsoValue = (event.action) match {
    case (REPONSE_PRO_CONTACT)                 => StatusConso.A_INFORMER_TRANSMISSION
    case (REPONSE_PRO_SIGNALEMENT)             => StatusConso.A_INFORMER_REPONSE_PRO
    case (EMAIL_NON_PRISE_EN_COMPTE)           => StatusConso.FAIT
    case (EMAIL_TRANSMISSION)                  => StatusConso.EN_ATTENTE
    case (EMAIL_REPONSE_PRO)                   => StatusConso.FAIT
    case (_)                                   => StatusConso.fromValue(previousStatus.getOrElse("")).getOrElse(EN_ATTENTE)
  }

  def createEvent(uuid: String) = SecuredAction.async(parse.json) { implicit request =>

    logger.debug("createEvent")

    request.body.validate[Event].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      event => {
        Try(UUID.fromString(uuid)) match {
          case Failure(_) => Future.successful(PreconditionFailed)
          case Success(id) => {
            for {
              report <- reportRepository.getReport(id)
              user <- userRepository.get(event.userId)
              _ <- report.flatMap(r => user.flatMap(u => u.id.map(id => reportRepository.createEvent(
                event.copy(
                  id = Some(UUID.randomUUID()),
                  creationDate = Some(LocalDateTime.now()),
                  reportId = r.id,
                  userId = id
                ))))).getOrElse(Future(None))
              _ <- report.map(r => reportRepository.update{
                  r.copy(statusPro = Some(determineStatusPro(event).value))
                  r.copy(statusConso = Some(determineStatusConso(event, r.statusConso).value))
              }).getOrElse(Future(None))
            } yield {
              (report, user) match {
                case (_, None) => BadRequest
                case (Some(_), _) => Ok(Json.toJson(event))
                case (None, _) => NotFound
              }
            }
          }
        }
      }
    )
  }


  def createReport = UserAwareAction.async(parse.json) { implicit request =>

    logger.debug("createReport")

    request.body.validate[Report].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      report => {
        for {
          report <- reportRepository.create(
            report.copy(
              id = Some(UUID.randomUUID()),
              creationDate = Some(LocalDateTime.now()),
              statusPro = determineStatusPro(report).map(s => s.value),
              statusConso = determineStatusConso(report).map(s => s.value)
            )
          )
          attachFilesToReport <- reportRepository.attachFilesToReport(report.files.map(_.id), report.id.get)
          files <- reportRepository.retrieveReportFiles(report.id.get)
          mailNotification <- sendReportNotificationByMail(report, files)
          mailAcknowledgment <- sendReportAcknowledgmentByMail(report, files)
        } yield {
          Ok(Json.toJson(report))
        }
      }
    )
  }

  def updateReport = SecuredAction.async(parse.json) { implicit request =>

    logger.debug("updateReport")

    request.body.validate[Report].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      report => {

        report.id match {
          case None => Future.successful(BadRequest)
          case Some(id) => {
            for {
              existingReport <- reportRepository.getReport(id)
              _ <- existingReport.map(r => reportRepository.update(r.copy(
                  firstName = report.firstName,
                  lastName = report.lastName,
                  email= report.email,
                  contactAgreement = report.contactAgreement,
                  companyName = report.companyName,
                  companyAddress = report.companyAddress,
                  companyPostalCode = report.companyPostalCode,
                  companySiret = report.companySiret,
                  statusPro = determineStatusPro(report).map(s => s.value)
                ))
              ).getOrElse(Future.successful(None))
            } yield {
              existingReport match {
                case Some(_) => Ok
                case None => NotFound
              }
            }
          }
        }

    })

  }

  def uploadReportFile = UserAwareAction.async(parse.multipartFormData(handleFilePartAwsUploadResult)) { request =>
    logger.debug("uploadReportFile")

    val maybeUploadResult =
      request.body.file("reportFile").map {
        case FilePart(key, filename, contentType, multipartUploadResult) =>
          (multipartUploadResult, filename)
      }

    maybeUploadResult.fold(Future(InternalServerError("Echec de l'upload"))) {
      maybeUploadResult =>
        reportRepository.createFile(
          File(UUID.fromString(maybeUploadResult._1.key), None, LocalDateTime.now(), maybeUploadResult._2)
        ).map(file => Ok(Json.toJson(file)))
    }
  }

  private def handleFilePartAwsUploadResult: Multipart.FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, filename, contentType) =>
      val accumulator = Accumulator(s3Service.upload(BucketName, UUID.randomUUID.toString))

      accumulator map { multipartUploadResult =>
        FilePart(partName, filename, contentType, multipartUploadResult)
      }
  }

  def sendReportNotificationByMail(report: Report, files: List[File])(implicit request: play.api.mvc.Request[Any]) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = configuration.get[String]("play.mail.contactRecipient"))(
      subject = "Nouveau signalement",
      bodyHtml = views.html.mails.reportNotification(report, files).toString
    ))
  }

  def sendReportAcknowledgmentByMail(report: Report, files: List[File]) = {
    report.category match {
      case "Intoxication alimentaire" => Future(())
      case _ =>
        Future(mailerService.sendEmail(
          from = configuration.get[String]("play.mail.from"),
          recipients = report.email)(
          subject = "Votre signalement",
          bodyHtml = views.html.mails.reportAcknowledgment(report, configuration.get[String]("play.mail.contactRecipient"), files).toString,
          attachments = Seq(
            AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo")),
            AttachmentFile("questionnaire.png", environment.getFile("/appfiles/questionnaire.png"), contentId = Some("questionnaire"))
          )
        ))
    }
  }

  def downloadReportFile(uuid: String, filename: String) = UserAwareAction.async { implicit request =>

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

  def deleteReportFile(uuid: String, filename: String) = UserAwareAction.async { implicit request =>
    logger.debug("deleteReportFile")

    reportRepository.getFile(UUID.fromString(uuid)).flatMap(_ match {
      case Some(file) if file.filename == filename =>
        for {
          repositoryDelete <- reportRepository.deleteFile(UUID.fromString(uuid))
          s3Delete <- s3Service.delete(BucketName, uuid)
        } yield NoContent
      case _ => Future(NotFound)
    })
  }

  def getStatistics = UserAwareAction.async { implicit request =>

    for {
      reportsCount <- reportRepository.count
      reportsPerMonth <- reportRepository.countPerMonth
    } yield {
      Ok(Json.toJson(
        Statistics(
          reportsCount,
          reportsPerMonth.filter(stat => stat.yearMonth.isAfter(YearMonth.now().minusYears(1)))
        )
      ))
    }
  }

  def getReport(uuid: String) = SecuredAction.async {

    logger.debug("getReport")

    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => {
        reportRepository.getReport(id).flatMap(_ match {
          case Some(report) => Future.successful(Ok(Json.toJson(report)))
          case None => Future.successful(NotFound)
        })
      }
    }
  }

  def deleteReport(uuid: String) = SecuredAction.async {

    logger.debug("deleteReport")

    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => {
        for {
          report <- reportRepository.getReport(id)
          _ <- reportRepository.deleteEvents(id)
          _ <- reportRepository.delete(id)
        } yield {
          report match {
            case None => NotFound
            case _ => NoContent
          }
        }
      }
    }

  }

  def getReports(
    offset: Option[Long], 
    limit: Option[Int], 
    sort: Option[String],
    departments: Option[String],
    email: Option[String],
    siret: Option[String],
    entreprise: Option[String],
    start: Option[String],
    end: Option[String]

  ) = SecuredAction.async { implicit request =>

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    val offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    val limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseDate(end)

    val filter = ReportFilter(departments.map(d => d.split(",").toSeq).getOrElse(Seq()), email, siret,entreprise, startDate, endDate)
    logger.debug(s"ReportFilter $filter")
    reportRepository.getReports(offsetNormalized, limitNormalized, filter).flatMap( reports => {

      Future.successful(Ok(Json.toJson(reports)))
    })

  }

  def getEvents(uuid: String, eventType: Option[String]) = SecuredAction.async {

    val filter = eventType match {
      case Some(_) => EventFilter(eventType = EventType.fromValue(eventType.get))
      case None => EventFilter(eventType = None)
    }

    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => {
        for {
          report <- reportRepository.getReport(id)
          events <- reportRepository.getEvents(id, filter)
        } yield {
          report match {
            case Some(_) => Ok(Json.toJson(events))
            case None => NotFound
          }
        }
      }}

  }

  def extractReports(departments: Option[String], start: Option[String], end: Option[String]) = SecuredAction.async { implicit request =>

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseDate(end)

    reportRepository.getReports(
      0,
      10000,
      ReportFilter(departments.map(d => d.split(",").toSeq).getOrElse(Seq()), start = startDate, end = endDate)
    ).flatMap( reports => {

      val csvFields = Array(
        "Date de création",
        "Département",
        "Nom de l'établissement",
        "Adresse de l'établissement",
        "Catégorie",
        "Sous-catégories",
        "Détails",
        "Prénom",
        "Nom",
        "Email",
        "Accord pour contact",
        "Pièces jointes",
        "Statut pro",
        "Statut conso",
        "Identifiant"
      ).reduce((s1, s2) => s"$s1;$s2")

      val csvData = reports.entities.map(report =>
        Array(
          report.creationDate.map(_.format(DateTimeFormatter.ofPattern(("dd/MM/yyyy")))).getOrElse(""),
          report.companyPostalCode match {
            case Some(codePostal) if codePostal.length >= 2 => codePostal.substring(0,2)
            case _ => ""
          },
          report.companyName,
          report.companyAddress,
          report.category,
          report.subcategories.reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
          report.details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}").reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
          report.lastName,
          report.firstName,
          report.email,
          report.contactAgreement match {
            case true => "Oui"
            case _ => "Non"
          },
          report.files
            .map(file => routes.ReportController.downloadReportFile(file.id.toString, file.filename).absoluteURL())
            .reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
          report.statusPro.getOrElse(""),
          report.statusConso.getOrElse(""),
          report.id.map(_.toString).getOrElse("")
        ).map(s => ("\"").concat(s"$s".replace("\"","\"\"").replace("&#160;", " ").concat("\"")))
          .reduce((s1, s2) => s"$s1;$s2")
      ).reduceOption((s1, s2) => s"$s1\n$s2")

      Future(
        Result(
        header = ResponseHeader(200, Map.empty),
        body = HttpEntity.Strict(ByteString(s"$csvFields${csvData.map(data => s"\n$data").getOrElse("")}"), Some("text/csv"))
      ))
    })

  }

}
