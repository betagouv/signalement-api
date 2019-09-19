package controllers

import java.io.File
import java.time.{LocalDateTime, OffsetDateTime}
import java.time.format.DateTimeFormatter
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
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.StatusConso._
import utils.Constants.StatusPro._
import utils.Constants.{Departments, EventType, StatusPro}
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.{AuthEnv, WithPermission}
import utils.{Constants, DateUtils}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class ReportController @Inject()(reportRepository: ReportRepository,
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

  def determineStatusPro(report: Report): StatusProValue = {
    if (report.isEligible) A_TRAITER else NA
  }

  def determineStatusConso(report: Report): StatusConsoValue = {
    if (report.isEligible) EN_ATTENTE else FAIT
  }

  def determineStatusPro(event: Event, previousStatus: Option[StatusProValue]): StatusProValue = (event.action, event.resultAction) match {
    case (A_CONTACTER, _)                      => A_TRAITER
    case (HORS_PERIMETRE, _)                   => NA
    case (CONTACT_TEL, _)                      => TRAITEMENT_EN_COURS
    case (CONTACT_EMAIL, _)                    => TRAITEMENT_EN_COURS
    case (CONTACT_COURRIER, _)                 => TRAITEMENT_EN_COURS
    case (RETOUR_COURRIER, _)                  => ADRESSE_INCORRECTE
    case (REPONSE_PRO_CONTACT, Some(true))     => A_TRANSFERER_SIGNALEMENT
    case (REPONSE_PRO_CONTACT, Some(false))    => SIGNALEMENT_NON_CONSULTE
    case (ENVOI_SIGNALEMENT, _)                => SIGNALEMENT_TRANSMIS
    case (REPONSE_PRO_SIGNALEMENT, Some(true)) => PROMESSE_ACTION
    case (REPONSE_PRO_SIGNALEMENT, _)          => SIGNALEMENT_INFONDE
    case (MAL_ATTRIBUE, _)                     => SIGNALEMENT_MAL_ATTRIBUE
    case (NON_CONSULTE, _)                     => SIGNALEMENT_NON_CONSULTE
    case (CONSULTE_IGNORE, _)                  => SIGNALEMENT_CONSULTE_IGNORE
    case (_, _)                                => previousStatus.getOrElse(NA)

  }

  def determineStatusConso(event: Event, previousStatus: Option[StatusConsoValue]): StatusConsoValue = (event.action) match {
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
    case _                                   => previousStatus.getOrElse(EN_ATTENTE)
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
              _ <- swap(report.flatMap(r => user.map(u => eventRepository.createEvent(
                event.copy(
                  id = Some(UUID.randomUUID()),
                  creationDate = Some(OffsetDateTime.now()),
                  reportId = r.id,
                  userId = u.id
                )))))
              newReport <- swap(report.map(r => reportRepository.update{
                  r.copy(
                    statusPro = Some(determineStatusPro(event, r.statusPro)),
                    statusConso = Some(determineStatusConso(event, r.statusConso)))
              }))
              _ <- swap(newReport.flatMap(r => (user, event.action) match {
                case (_, ENVOI_SIGNALEMENT) => Some(notifyConsumerOfReportTransmission(r, request.identity.id))
                case (Some(u), REPONSE_PRO_SIGNALEMENT) => Some(sendMailsAfterProAcknowledgment(r, event, u))
                case (_, MAL_ATTRIBUE) => Some(sendMailWrongAssignment(r, event))
                case (_, NON_CONSULTE) => Some(sendMailClosedByNoReading(r))
                case (_, CONSULTE_IGNORE) => Some(sendMailClosedByNoAction(r))
                case _ => None
              }))
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
              creationDate = Some(OffsetDateTime.now()),
              statusPro = Some(determineStatusPro(report)),
              statusConso = Some(determineStatusConso(report))
            )
          )
          _ <- reportRepository.attachFilesToReport(report.files.map(_.id), report.id.get)
          files <- reportRepository.retrieveReportFiles(report.id.get)
          _ <- sendMailAdminReportNotification(report, files)
          _ <- sendMailReportAcknowledgment(report, files)
          _ <- (report.companySiret, report.isEligible) match {
            case (Some(_), true) => notifyProfessionalOfNewReport(report)
            case _ => Future(None)
          }
        } yield {
          Ok(Json.toJson(report))
        }
      }
    )
  }

  def notifyProfessionalOfNewReport(report: Report) = {
    for {
      eitherUserOrKey <- userRepository.findByLogin(report.companySiret.get).map(user => user.map(_ => Left(user.get)).getOrElse(Right(f"${Random.nextInt(1000000)}%06d")))
      _ <- eitherUserOrKey match {
        case Left(user) if user.email.isDefined =>
          for {
            _ <- sendMailProfessionalReportNotification(report, user)
            event <- eventRepository.createEvent(
              Event(
                Some(UUID.randomUUID()),
                report.id,
                user.id,
                Some(OffsetDateTime.now()),
                Constants.EventType.PRO,
                Constants.ActionEvent.CONTACT_EMAIL,
                None,
                Some(s"Notification du professionnel par mail de la réception d'un nouveau signalement ( ${user.email.getOrElse("") } )")
              )
            )
            _ <- reportRepository.update(
              report.copy(
                statusPro = Some(determineStatusPro(event, report.statusPro)),
                statusConso = Some(determineStatusConso(event, report.statusConso))
              )
            )
          } yield ()
        case Right(activationKey) =>
          userRepository.create(
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
          )
        case _ => Future(None)
      }
    } yield ()
  }

  // utility : swap Option[Future[X]] in Future[Option[X]]
  // @see : https://stackoverflow.com/questions/38226203/scala-optionfuturet-to-futureoptiont
  def swap[M](x: Option[Future[M]]): Future[Option[M]] =
    Future.sequence(Option.option2Iterable(x)).map(_.headOption)

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
              resultReport <- swap(existingReport.map(r => reportRepository.update(r.copy(
                  firstName = report.firstName,
                  lastName = report.lastName,
                  email= report.email,
                  contactAgreement = report.contactAgreement,
                  companyName = report.companyName,
                  companyAddress = report.companyAddress,
                  companyPostalCode = report.companyPostalCode,
                  companySiret = report.companySiret
                ))
              ))
              _ <- (existingReport.map(_.companySiret).getOrElse(None), report.companySiret, report.isEligible, resultReport) match {
                case (someExistingSiret, Some(siret), true, Some(newReport)) if (someExistingSiret != Some(siret)) => notifyProfessionalOfNewReport(newReport)
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
          ReportFile(UUID.fromString(maybeUploadResult._1.key), None, OffsetDateTime.now(), maybeUploadResult._2)
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

  private def sendMailAdminReportNotification(report: Report, files: List[ReportFile])(implicit request: play.api.mvc.Request[Any]) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = configuration.get[String]("play.mail.contactRecipient"))(
      subject = "Nouveau signalement",
      bodyHtml = views.html.mails.admin.reportNotification(report, files).toString
    ))
  }

  private def sendMailProfessionalReportNotification(report: Report, professionalUser: User) = {

    professionalUser.email match {
      case Some(mail) if mail != "" => Future(mailerService.sendEmail(
        from = configuration.get[String]("play.mail.from"),
        recipients = mail)(
        subject = "Nouveau signalement",
        bodyHtml = views.html.mails.professional.reportNotification(report).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      ))
      case _ => Future(None)
    }
  }

  private def sendMailReportAcknowledgment(report: Report, files: List[ReportFile]) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = report.email)(
      subject = "Votre signalement",
      bodyHtml = views.html.mails.consumer.reportAcknowledgment(report, files).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
  }

  private def sendMailsAfterProAcknowledgment(report: Report, event: Event, user: User) = {
      for {
        _ <- sendMailToProForAcknowledgmentPro(event, user)
        _ <- sendMailToConsumerForReportAcknowledgmentPro(report, event)
      } yield {
        logger.debug("Envoi d'email au professionnel et au consommateur suite à réponse du professionnel")
      }
  }

  private def sendMailWrongAssignment(report: Report, event: Event) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = report.email)(
      subject = "Le professionnel a répondu à votre signalement",
      bodyHtml = views.html.mails.consumer.reportWrongAssignment(report, event).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
  }

  private def sendMailClosedByNoReading(report: Report) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = report.email)(
      subject = "Le professionnel n’a pas souhaité consulter votre signalement",
      bodyHtml = views.html.mails.consumer.reportClosedByNoReading(report).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
  }

  private def sendMailClosedByNoAction(report: Report) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = report.email)(
      subject = "Le professionnel n’a pas répondu au signalement",
      bodyHtml = views.html.mails.consumer.reportClosedByNoAction(report).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
  }

  private def sendMailToProForAcknowledgmentPro(event: Event, user: User) = {

    user.email match {
      case Some(mail) if mail != "" => Future(mailerService.sendEmail(
        from = configuration.get[String]("play.mail.from"),
        recipients = mail)(
        subject = "Votre réponse au signalement",
        bodyHtml = views.html.mails.professional.reportAcknowledgmentPro(event, user).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      ))
      case _ => Future(None)
    }

  }

  private def sendMailToConsumerForReportAcknowledgmentPro(report: Report, event: Event) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = report.email)(
      subject = "Le professionnel a répondu à votre signalement",
      bodyHtml = views.html.mails.consumer.reportToConsumerAcknowledgmentPro(report, event).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
  }


  private def sendMailReportTransmission(report: Report) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = report.email)(
      subject = "Votre signalement",
      bodyHtml = views.html.mails.consumer.reportTransmission(report).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
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

  def getReport(uuid: String) = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>

    logger.debug("getReport")

    implicit val reportWriter = request.identity.userRole match {
      case UserRoles.Pro => Report.reportProWriter
      case _ => Report.reportWriter
    }

    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) => {
        reportRepository.getReport(id).map { rep =>
          rep match {
            case Some(r) => Some(r.copy(statusPro = StatusPro.fromValue(getGenericStatusProWithUserRole(r.statusPro, request.identity.userRole))))
            case _ => None
          }
        }.flatMap(report => (report, request.identity.userRole) match {
          case (Some(report), UserRoles.Pro) if report.companySiret != Some(request.identity.login)  => Future.successful(Unauthorized)
          case (Some(report), UserRoles.Pro) =>
            for {
              events <- eventRepository.getEvents(UUID.fromString(uuid), EventFilter(None))
              firstView <- Future(!events.exists(event => event.action == Constants.ActionEvent.ENVOI_SIGNALEMENT))
              report <- firstView match {
                case true => manageFirstViewOfReportByPro(report, request.identity.id)
                case false => Future(report)
              }
            } yield {
              Ok(Json.toJson(report))
            }
          case (Some(report), _) => Future.successful(Ok(Json.toJson(report)))
          case (None, _) => Future.successful(NotFound)
        })
      }
    }
  }

  def manageFirstViewOfReportByPro(report: Report, userUUID: UUID) = {
    for {
      event <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          report.id,
          userUUID,
          Some(OffsetDateTime.now()),
          Constants.EventType.PRO,
          Constants.ActionEvent.ENVOI_SIGNALEMENT,
          None,
          Some("Première consultation du détail du signalement par le professionnel")
        )
      )
      report <- reportRepository.update(
        report.copy(
          statusPro = Some(determineStatusPro(event, report.statusPro)),
          statusConso = Some(determineStatusConso(event, report.statusConso))
        )
      )
      report <- notifyConsumerOfReportTransmission(report, userUUID)
    } yield (report)
  }

  def notifyConsumerOfReportTransmission(report: Report, userUUID: UUID) = {
    for {
      _ <- sendMailReportTransmission(report)
      event <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          report.id,
          userUUID,
          Some(OffsetDateTime.now()),
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_TRANSMISSION,
          None,
          Some("Envoi email au consommateur d'information de transmission")
        )
      )
      newReport <- reportRepository.update(
        report.copy(
          statusPro = Some(determineStatusPro(event, report.statusPro)),
          statusConso = Some(determineStatusConso(event, report.statusConso))
        )
      )
    } yield (newReport)
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

  def getReportCountBySiret(siret: String) = silhouetteAPIKey.SecuredAction.async {
    reportRepository.count(Some(siret)).flatMap(count => Future(Ok(Json.obj("siret" -> siret, "count" -> count))))
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
