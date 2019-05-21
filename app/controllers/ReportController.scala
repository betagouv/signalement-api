package controllers

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, YearMonth}
import java.util.UUID

import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import akka.util.ByteString
import com.mohiva.play.silhouette.api.Silhouette
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.{CellFill, CellHorizontalAlignment, CellVerticalAlignment}
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
import javax.inject.Inject
import models._
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
import utils.Constants.StatusConso._
import utils.Constants.StatusPro._
import utils.Constants.{EventType, StatusConso, StatusPro}
import utils.DateUtils
import utils.silhouette.{AuthEnv, WithPermission}

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
    "18", "28", "36", "37", "41", "45", // CVDL
    "09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82" // OCC
  )

  def determineStatusPro(report: Report): StatusProValue = {

    if (departmentsAuthorized.contains(report.companyPostalCode.get.slice(0, 2))) A_TRAITER else NA
  }

  def determineStatusConso(report: Report): StatusConsoValue = {

    if (departmentsAuthorized.contains(report.companyPostalCode.get.slice(0, 2))) EN_ATTENTE else A_RECONTACTER
  }


  def determineStatusPro(event: Event, previousStatus: Option[String]): StatusProValue = (event.action, event.resultAction) match {
    case (A_CONTACTER, _)                      => A_TRAITER
    case (HORS_PERIMETRE, _)                   => NA
    case (CONTACT_TEL, _)                      => TRAITEMENT_EN_COURS
    case (CONTACT_EMAIL, _)                    => TRAITEMENT_EN_COURS
    case (CONTACT_COURRIER, _)                 => TRAITEMENT_EN_COURS
    case (REPONSE_PRO_CONTACT, Some(true))     => A_TRANSFERER_SIGNALEMENT
    case (REPONSE_PRO_CONTACT, Some(false))    => SIGNALEMENT_REFUSE
    case (ENVOI_SIGNALEMENT, _)                => SIGNALEMENT_TRANSMIS
    case (REPONSE_PRO_SIGNALEMENT, Some(true)) => PROMESSE_ACTION
    case (REPONSE_PRO_SIGNALEMENT, _)          => PROMESSE_ACTION_REFUSEE
    case (_, _)                                => StatusPro.fromValue(previousStatus.getOrElse("")).getOrElse(NA)

  }

  def determineStatusConso(event: Event, previousStatus: Option[String]): StatusConsoValue = (event.action) match {
    case (ENVOI_SIGNALEMENT)                   => A_INFORMER_TRANSMISSION
    case (REPONSE_PRO_SIGNALEMENT)             => A_INFORMER_REPONSE_PRO
    case (EMAIL_NON_PRISE_EN_COMPTE)           => FAIT
    case (EMAIL_TRANSMISSION)                  => EN_ATTENTE
    case (EMAIL_REPONSE_PRO)                   => FAIT
    case (_)                                   => StatusConso.fromValue(previousStatus.getOrElse("")).getOrElse(EN_ATTENTE)
  }

  def createEvent(uuid: String) = SecuredAction(WithPermission(UserPermission.createEvent)).async(parse.json) { implicit request =>

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
                  r.copy(
                    statusPro = Some(determineStatusPro(event, r.statusPro).value),
                    statusConso = Some(determineStatusConso(event, r.statusConso).value))
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


  def createReport = UnsecuredAction.async(parse.json) { implicit request =>

    logger.debug("createReport")

    request.body.validate[Report].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      report => {
        for {
          report <- reportRepository.create(
            report.copy(
              id = Some(UUID.randomUUID()),
              creationDate = Some(LocalDateTime.now()),
              statusPro = Some(determineStatusPro(report).value),
              statusConso = Some(determineStatusConso(report).value)
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

  def updateReport = SecuredAction(WithPermission(UserPermission.updateReport)).async(parse.json) { implicit request =>

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
                  statusPro = Some(determineStatusPro(report).value)
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

  def uploadReportFile = UnsecuredAction.async(parse.multipartFormData(handleFilePartAwsUploadResult)) { request =>
    logger.debug("uploadReportFile")

    val maybeUploadResult =
      request.body.file("reportFile").map {
        case FilePart(key, filename, contentType, multipartUploadResult) =>
          (multipartUploadResult, filename)
      }

    maybeUploadResult.fold(Future(InternalServerError("Echec de l'upload"))) {
      maybeUploadResult =>
        reportRepository.createFile(
          ReportFile(UUID.fromString(maybeUploadResult._1.key), None, LocalDateTime.now(), maybeUploadResult._2)
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

  private def sendReportNotificationByMail(report: Report, files: List[ReportFile])(implicit request: play.api.mvc.Request[Any]) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = configuration.get[String]("play.mail.contactRecipient"))(
      subject = "Nouveau signalement",
      bodyHtml = views.html.mails.reportNotification(report, files).toString
    ))
  }

  private def sendReportAcknowledgmentByMail(report: Report, files: List[ReportFile]) = {
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

  def deleteReportFile(uuid: String, filename: String) = UserAwareAction.async { implicit request =>
    logger.debug("deleteReportFile")

    reportRepository.getFile(UUID.fromString(uuid)).flatMap(_ match {
      case Some(file) if file.filename == filename =>
        (file.reportId, request.identity) match {
          case (None, _) =>
            for {
              repositoryDelete <- reportRepository.deleteFile(UUID.fromString(uuid))
              s3Delete <- s3Service.delete(BucketName, uuid)
            } yield NoContent
          case (Some(reportId), Some(identity)) if identity.userRole.permissions.contains(UserPermission.deleteFile) =>
            for {
              repositoryDelete <- reportRepository.deleteFile(UUID.fromString(uuid))
              s3Delete <- s3Service.delete(BucketName, uuid)
            } yield NoContent
          case (_, _) => Future(Forbidden)
        }
      case _ => Future(NotFound)
    })
  }

  def getStatistics = UnsecuredAction.async { implicit request =>

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

  def getReport(uuid: String) = SecuredAction(WithPermission(UserPermission.listReports)).async {

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

  def deleteReport(uuid: String) = SecuredAction(WithPermission(UserPermission.deleteReport)).async {

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
    companyName: Option[String],
    start: Option[String],
    end: Option[String],
    category: Option[String],
    statusPro: Option[String]

  ) = SecuredAction.async { implicit request =>

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    val offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    val limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseDate(end)

    val filter = ReportFilter(departments.map(d => d.split(",").toSeq).getOrElse(Seq()), email, siret,companyName, startDate, endDate, category, statusPro)
    logger.debug(s"ReportFilter $filter")
    reportRepository.getReports(offsetNormalized, limitNormalized, filter).flatMap( reports => {

      Future.successful(Ok(Json.toJson(reports)))
    })

  }

  def getEvents(uuid: String, eventType: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async {

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

  def extractReports(departments: Option[String], start: Option[String], end: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseDate(end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    logger.debug(s"role ${request.identity.userRole}")

    for {
      result <- reportRepository.getReports(
        0,
        10000,
        ReportFilter(departments.map(d => d.split(",").toSeq).getOrElse(Seq()), start = startDate, end = endDate)
      )
      reports <- Future(result.entities)
      reportsData <- Future.sequence(reports.map(extractDataFromReport(_)))
    } yield {

      val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.Gainsborough, font = Font(bold = true), horizontalAlignment = CellHorizontalAlignment.Center)
      val centerAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Center, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
      val leftAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Left, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
      
      val fields = List(
        ("Date de création", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Département", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Siret", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Nom de l'établissement", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Adresse de l'établissement", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Catégorie", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Sous-catégories", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Détails", Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle)),
        ("Prénom", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Nom", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Email", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Accord pour contact", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Pièces jointes", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Statut pro", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Détail promesse d'action", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Statut conso", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Identifiant", Column(autoSized = true, style = centerAlignmentStyle))
      )

      val tmpFileName = s"${configuration.get[String]("play.tmpDirectory")}/signalements.xlsx";
      val reportsSheet = Sheet(name = "Signalements")
        .withRows(
          Row(style = headerStyle).withCellValues(fields.map(_._1)) ::
          reportsData.map(reportData => {
            Row().withCellValues(reportData)
          })
        )
        .withColumns(
          fields.map(_._2)
        )

      reportsSheet.saveAsXlsx(tmpFileName)

      Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
    }

  }

  private def extractDataFromReport(report: Report)(implicit request: play.api.mvc.Request[Any]) = {

    reportRepository.getEvents(report.id.get, EventFilter(Some(EventType.PRO))).flatMap(events => {
      Future(
        List(
          report.creationDate.map(_.format(DateTimeFormatter.ofPattern(("dd/MM/yyyy")))).getOrElse(""),
          report.companyPostalCode match {
            case Some(codePostal) if codePostal.length >= 2 => codePostal.substring(0,2)
            case _ => ""
          },
          report.companySiret.getOrElse(""),
          report.companyName,
          report.companyAddress,
          report.category,
          report.subcategories.filter(s => s != null).reduceOption((s1, s2) => s"$s1\n$s2").getOrElse("").replace("&#160;", " "),
          report.details.map(detailInputValue => s"${detailInputValue.label.replace("&#160;", " ")} ${detailInputValue.value}").reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
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
          report.statusPro
            .filter(StatusPro.fromValue(_) == Some(StatusPro.PROMESSE_ACTION))
            .filter(_ => events.length > 0)
            .flatMap(_ => events.head.detail).getOrElse(""),
          report.statusConso.getOrElse(""),
          report.id.map(_.toString).getOrElse("")
        )
      )
    })


  }

}
