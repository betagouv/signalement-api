package controllers

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, YearMonth}
import java.util.UUID

import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import com.mohiva.play.silhouette.api.Silhouette
import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.model.enums.{CellFill, CellHorizontalAlignment, CellVerticalAlignment}
import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
import javax.inject.Inject
import models._
import play.api.libs.json.{JsError, Json}
import play.api.libs.mailer.AttachmentFile
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.{Configuration, Environment, Logger}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import repositories.{EventFilter, EventRepository, ReportFilter, ReportRepository, UserRepository}
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.StatusConso._
import utils.Constants.StatusPro._
import utils.Constants.{EventType, StatusConso, StatusPro}
import utils.{Constants, DateUtils}
import utils.silhouette.{AuthEnv, WithPermission}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class ReportController @Inject()(reportRepository: ReportRepository,
                                 eventRepository: EventRepository,
                                 userRepository: UserRepository,
                                 mailerService: MailerService,
                                 s3Service: S3Service,
                                 val silhouette: Silhouette[AuthEnv],
                                 configuration: Configuration,
                                 environment: Environment)
                                (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  val BucketName = configuration.get[String]("play.buckets.report")

  val AURA = List("01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74")
  val CDVL = List("18", "28", "36", "37", "41", "45")
  val OCC = List("09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82")

  val departmentsAuthorized = AURA ++ CDVL ++ OCC


  def departmentAuthorized(report: Report) = {
    report.companyPostalCode.map(postalCode => departmentsAuthorized.contains(postalCode.slice(0, 2))).getOrElse(false);
  }

  def determineStatusPro(report: Report): StatusProValue = {
    if (departmentAuthorized(report)) A_TRAITER else NA
  }

  def determineStatusConso(report: Report): StatusConsoValue = {
    if (departmentAuthorized(report)) EN_ATTENTE else A_RECONTACTER
  }

  def determineStatusPro(event: Event, previousStatus: Option[String]): StatusProValue = (event.action, event.resultAction) match {
    case (A_CONTACTER, _)                      => A_TRAITER
    case (HORS_PERIMETRE, _)                   => NA
    case (CONTACT_TEL, _)                      => TRAITEMENT_EN_COURS
    case (CONTACT_EMAIL, _)                    => TRAITEMENT_EN_COURS
    case (CONTACT_COURRIER, _)                 => TRAITEMENT_EN_COURS
    case (RETOUR_COURRIER, _)                  => ADRESSE_INCORRECTE
    case (REPONSE_PRO_CONTACT, Some(true))     => A_TRANSFERER_SIGNALEMENT
    case (REPONSE_PRO_CONTACT, Some(false))    => SIGNALEMENT_REFUSE
    case (ENVOI_SIGNALEMENT, _)                => SIGNALEMENT_TRANSMIS
    case (REPONSE_PRO_SIGNALEMENT, Some(true)) => PROMESSE_ACTION
    case (REPONSE_PRO_SIGNALEMENT, _)          => PROMESSE_ACTION_REFUSEE
    case (_, _)                                => StatusPro.fromValue(previousStatus.getOrElse("")).getOrElse(NA)

  }

  def determineStatusConso(event: Event, previousStatus: Option[String]): StatusConsoValue = (event.action) match {
    case A_CONTACTER                         => EN_ATTENTE
    case ENVOI_SIGNALEMENT                   => A_INFORMER_TRANSMISSION
    case REPONSE_PRO_SIGNALEMENT             => A_INFORMER_REPONSE_PRO
    case EMAIL_NON_PRISE_EN_COMPTE           => FAIT
    case EMAIL_TRANSMISSION                  => EN_ATTENTE
    case EMAIL_REPONSE_PRO                   => FAIT
    case CONTACT_TEL                         => EN_ATTENTE
    case CONTACT_EMAIL                       => EN_ATTENTE
    case CONTACT_COURRIER                    => EN_ATTENTE
    case HORS_PERIMETRE                      => A_RECONTACTER
    case _                                   => StatusConso.fromValue(previousStatus.getOrElse("")).getOrElse(EN_ATTENTE)
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
              _ <- report.flatMap(r => user.map(u => eventRepository.createEvent(
                event.copy(
                  id = Some(UUID.randomUUID()),
                  creationDate = Some(LocalDateTime.now()),
                  reportId = r.id,
                  userId = u.id
                )))).getOrElse(Future(None))
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
          _ <- createUserToActivateForReport(report)
        } yield {
          Ok(Json.toJson(report))
        }
      }
    )
  }

  def createUserToActivateForReport(report: Report) = {
    for {
      activationKey <- (report.companySiret, departmentAuthorized(report)) match {
        case (Some(siret), true) => userRepository.findByLogin(siret).map(user => user.map(_ => None).getOrElse(Some(f"${Random.nextInt(1000000)}%06d")))
        case _ => Future(None)
      }
      user <- activationKey.map(activationKey => userRepository.create(
        User(
          UUID.randomUUID(),
          report.companySiret.get,
          activationKey,
          Some(activationKey),
          None,
          None,
          None,
          UserRoles.ToActivate
        )
      )).getOrElse(Future(None))
    } yield user
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
                  companySiret = report.companySiret
                ))
              ).getOrElse(Future(None))
              _ <- (existingReport.map(_.companySiret).getOrElse(None), report.companySiret) match {
                case (someExistingSiret, Some(siret)) if (someExistingSiret != Some(siret)) => createUserToActivateForReport(report)
                case _ => Future(None)
              }
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

  def getStatistics = UserAwareAction.async { implicit request =>

    for {
      reportsCount <- reportRepository.count
      reportsPerMonth <- reportRepository.countPerMonth
      reportsCount7Days <- reportRepository.nbSignalementsBetweenDates(DateUtils.formatTime(LocalDateTime.now().minusDays(7)))
      reportsCount30Days <- reportRepository.nbSignalementsBetweenDates(DateUtils.formatTime(LocalDateTime.now().minusDays(30)))
      reportsCountInRegion <- reportRepository.nbSignalementsBetweenDates(departments = Some(departmentsAuthorized))
      reportsCount7DaysInRegion <- reportRepository.nbSignalementsBetweenDates(start = DateUtils.formatTime(LocalDateTime.now().minusDays(7)), departments = Some(departmentsAuthorized))
      reportsCount30DaysInRegion <- reportRepository.nbSignalementsBetweenDates(start = DateUtils.formatTime(LocalDateTime.now().minusDays(30)), departments = Some(departmentsAuthorized))
      reportsCountSendedToPro <- reportRepository.nbSignalementsBetweenDates(departments = Some(departmentsAuthorized), event = Some(ENVOI_SIGNALEMENT))
      reportsCountPromise <- reportRepository.nbSignalementsBetweenDates(departments = Some(departmentsAuthorized), event = Some(REPONSE_PRO_SIGNALEMENT))
      reportsCountWithoutSiret <- reportRepository.nbSignalementsBetweenDates(withoutSiret = true)
      reportsCountByCategory <- reportRepository.nbSignalementsByCategory()
      reportsCountAura <- reportRepository.nbSignalementsBetweenDates(departments = Some(AURA))
      reportsCountCdvl <- reportRepository.nbSignalementsBetweenDates(departments = Some(CDVL))
      reportsCountOcc <- reportRepository.nbSignalementsBetweenDates(departments = Some(OCC))
      reportsDurationsForEnvoiSignalement <- reportRepository.avgDurationsForEvent(ENVOI_SIGNALEMENT)

    } yield {

      val reportsCountByRegionList = Seq(
        ReportsByRegion("AURA", reportsCountAura.getOrElse(0)),
        ReportsByRegion("CDVL", reportsCountCdvl.getOrElse(0)),
        ReportsByRegion("OCC", reportsCountOcc.getOrElse(0)))

      Ok(Json.toJson(
        Statistics(
          reportsCount,
          reportsPerMonth.filter(stat => stat.yearMonth.isAfter(YearMonth.now().minusYears(1))),
          reportsCount7Days.getOrElse(0),
          reportsCount30Days.getOrElse(0),
          reportsCountInRegion.getOrElse(0),
          reportsCount7DaysInRegion.getOrElse(0),
          reportsCount30DaysInRegion.getOrElse(0),
          reportsCountSendedToPro.getOrElse(0),
          reportsCountPromise.getOrElse(0),
          reportsCountWithoutSiret.getOrElse(0),
          reportsCountByCategory.toList,
          reportsCountByRegionList,
          reportsDurationsForEnvoiSignalement.getOrElse(0)
        )
      ))
    }
  }

  def getReport(uuid: String) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>

    logger.debug("getReport")

    implicit val reportWriter = request.identity.userRole match {
      case UserRoles.Pro => Report.reportProWriter
      case _ => Report.reportWriter
    }

    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => {
        reportRepository.getReport(id).flatMap(report => (report, request.identity.userRole) match {
          case (Some(report), UserRoles.Pro) if report.companySiret != Some(request.identity.login)  => Future.successful(Unauthorized)
          case (Some(report), UserRoles.Pro) =>
            for {
              events <- eventRepository.getEvents(UUID.fromString(uuid), EventFilter(None))
              eventToCreate <- events.find(event => event.action == Constants.ActionEvent.ENVOI_SIGNALEMENT).map(_ => Future(None)).getOrElse(
                Future(Some(Event(
                  Some(UUID.randomUUID()),
                  report.id,
                  request.identity.id,
                  Some(LocalDateTime.now()),
                  Constants.EventType.CONSO,
                  Constants.ActionEvent.ENVOI_SIGNALEMENT,
                  None,
                  Some("Première consultation du détail du signalement par le professionnel")
                )))
              )
              _ <- eventToCreate.map(eventRepository.createEvent(_)).getOrElse(Future())
              _ <- eventToCreate.map(event => reportRepository.update(
                report.copy(
                  statusPro = Some(determineStatusPro(event, report.statusPro).value),
                  statusConso = Some(determineStatusConso(event, report.statusConso).value)
                )
              )).getOrElse(Future())
            } yield {
              Ok(Json.toJson(report))
            }
          case (Some(report), _) => Future.successful(Ok(Json.toJson(report)))
          case (None, _) => Future.successful(NotFound)
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
          _ <- eventRepository.deleteEvents(id)
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
    departments: Option[String],
    email: Option[String],
    siret: Option[String],
    companyName: Option[String],
    start: Option[String],
    end: Option[String],
    category: Option[String],
    statusPro: Option[String],
    statusConso: Option[String],
    details: Option[String]

  ) = SecuredAction.async { implicit request =>

    implicit val paginatedReportWriter = request.identity.userRole match {
      case UserRoles.Pro => PaginatedResult.paginatedReportProWriter
      case _ => PaginatedResult.paginatedReportWriter
    }

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    val offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    val limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseEndDate(end)

    val filter = ReportFilter(
      departments.map(d => d.split(",").toSeq).getOrElse(Seq()),
      email,
      request.identity.userRole match {
        case UserRoles.Pro => Some(request.identity.login)
        case _ => siret
      },
      companyName,
      startDate,
      endDate,
      category,
      statusPro,
      statusConso,
      details
    )

    logger.debug(s"ReportFilter $filter")
    reportRepository.getReports(offsetNormalized, limitNormalized, filter).flatMap( paginatedReports => {
      Future.successful(Ok(Json.toJson(paginatedReports)))
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
          events <- eventRepository.getEvents(id, filter)
        } yield {
          report match {
            case Some(_) => Ok(Json.toJson(events))
            case None => NotFound
          }
        }
      }}

  }

  def extractReports(departments: Option[String],
                     siret: Option[String],
                     start: Option[String],
                     end: Option[String],
                     category: Option[String],
                     statusPro: Option[String],
                     statusConso: Option[String],
                     details: Option[String]) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>

    val startDate = DateUtils.parseDate(start)
    val endDate = DateUtils.parseEndDate(end)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    logger.debug(s"role ${request.identity.userRole}")

    for {
      result <- reportRepository.getReports(
        0,
        10000,
        ReportFilter(departments.map(d => d.split(",").toSeq).getOrElse(Seq()), None, siret, None, startDate, endDate, category, statusPro, statusConso, details)
      )
      reports <- Future(result.entities)
      reportsData <- Future.sequence(reports.map(extractDataFromReport(_, request.identity.userRole)))
    } yield {

      val headerStyle = CellStyle(fillPattern = CellFill.Solid, fillForegroundColor = Color.Gainsborough, font = Font(bold = true), horizontalAlignment = CellHorizontalAlignment.Center)
      val centerAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Center, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
      val leftAlignmentStyle = CellStyle(horizontalAlignment = CellHorizontalAlignment.Left, verticalAlignment = CellVerticalAlignment.Center, wrapText = true)
      
      val fields = List(
        ("Date de création", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Département", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Code postal", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Siret", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Nom de l'établissement", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Adresse de l'établissement", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Catégorie", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Sous-catégories", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Détails", Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle)),
        ("Pièces jointes", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Statut pro", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Détail promesse d'action", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Statut conso", Column(autoSized = true, style = leftAlignmentStyle, hidden = (request.identity.userRole == UserRoles.DGCCRF))),
        ("Identifiant", Column(autoSized = true, style = centerAlignmentStyle)),
        ("Prénom", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Nom", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Email", Column(autoSized = true, style = leftAlignmentStyle)),
        ("Accord pour contact", Column(autoSized = true, style = centerAlignmentStyle))
      ) ::: (request.identity.userRole match {
        case UserRoles.Admin => List(
          ("Code d'activation", Column(autoSized = true, style = centerAlignmentStyle))
        )
        case _ => List()
      })

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

      val filtersSheet = Sheet(name = "Filtres")
        .withRows(
          List(
            Some(Row().withCellValues("Date de l'export", LocalDateTime.now().format(DateTimeFormatter.ofPattern(("dd/MM/yyyy à HH:mm:ss"))))),
            departments.map(departments => Row().withCellValues("Départment(s)", departments)),
            (startDate, DateUtils.parseDate(end)) match {
              case (Some(startDate), Some(endDate)) => Some(Row().withCellValues("Période", s"Du ${startDate.format(formatter)} au ${endDate.format(formatter)}"))
              case (Some(startDate), _) => Some(Row().withCellValues("Période", s"Depuis le ${startDate.format(formatter)}"))
              case (_, Some(endDate)) => Some(Row().withCellValues("Période", s"Jusqu'au ${endDate.format(formatter)}"))
              case(_) => None
            },
            siret.map(siret => Row().withCellValues("Siret", siret)),
            statusPro.map(statusPro => Row().withCellValues("Statut pro", statusPro)),
            statusConso.map(statusConso => Row().withCellValues("Statut conso", statusConso)),
            category.map(category => Row().withCellValues("Catégorie", category)),
            details.map(details => Row().withCellValues("Mots clés", details)),
          ).filter(_.isDefined).map(_.get)
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          Column(autoSized = true, style = leftAlignmentStyle)
        )

      Workbook(reportsSheet, filtersSheet).saveAsXlsx(tmpFileName)

      Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
    }

  }

  private def extractDataFromReport(report: Report, userRole: UserRole)(implicit request: play.api.mvc.Request[Any]) = {

    for {
      events <- eventRepository.getEvents(report.id.get, EventFilter(Some(EventType.PRO)))
      activationKey <- (report.companySiret, departmentAuthorized(report)) match {
        case (Some(siret), true) => userRepository.findByLogin(siret).map(user => user.map(_.activationKey).getOrElse(None))
        case _ => Future(None)
      }
    }
    yield (
      List(
        report.creationDate.map(_.format(DateTimeFormatter.ofPattern(("dd/MM/yyyy")))).getOrElse(""),
        report.companyPostalCode match {
          case Some(codePostal) if codePostal.length >= 2 => codePostal.substring(0,2)
          case _ => ""
        },
        report.companyPostalCode.getOrElse(""),
        report.companySiret.getOrElse(""),
        report.companyName,
        report.companyAddress,
        report.category,
        report.subcategories.filter(s => s != null).reduceOption((s1, s2) => s"$s1\n$s2").getOrElse("").replace("&#160;", " "),
        report.details.map(detailInputValue => s"${detailInputValue.label.replace("&#160;", " ")} ${detailInputValue.value}").reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
        report.files
          .map(file => routes.ReportController.downloadReportFile(file.id.toString, file.filename).absoluteURL())
          .reduceOption((s1, s2) => s"$s1\n$s2").getOrElse(""),
        report.statusPro.getOrElse(""),
        report.statusPro
          .filter(StatusPro.fromValue(_) == Some(StatusPro.PROMESSE_ACTION))
          .filter(_ => events.length > 0)
          .flatMap(_ => events.head.detail).getOrElse(""),
        report.statusConso.getOrElse(""),
        report.id.map(_.toString).getOrElse(""),
        report.firstName,
        report.lastName,
        report.email,
        report.contactAgreement match {
          case true => "Oui"
          case _ => "Non"
        }
      ) ::: {userRole match {
        case UserRoles.Admin => List(
          activationKey.getOrElse("")
        )
        case _ => List()
      }}
    )
  }

}
