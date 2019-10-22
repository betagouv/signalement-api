package orchestrators

import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID

import play.api.{Configuration, Environment, Logger}
import play.api.libs.mailer.AttachmentFile

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import models._
import models.Event._
import models.ReportResponse._
import play.api.libs.json.{Json, OFormat}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants
import utils.Constants.ActionEvent._
import utils.Constants.{ActionEvent, EventType}
import utils.Constants.ReportStatus._


class ReportOrchestrator @Inject()(reportRepository: ReportRepository,
                                   eventRepository: EventRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   s3Service: S3Service,
                                   configuration: Configuration,
                                   environment: Environment)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val bucketName = configuration.get[String]("play.buckets.report")
  val mailFrom = configuration.get[String]("play.mail.from")

  private def notifyProfessionalOfNewReport(report: Report): Future[Report] = {
    userRepository.findByLogin(report.companySiret.get).flatMap{
      case Some(user) if user.email.filter(_ != "").isDefined => {
        mailerService.sendEmail(
          from = mailFrom,
          recipients = user.email.get)(
          subject = "Nouveau signalement",
          bodyHtml = views.html.mails.professional.reportNotification(report).toString,
          attachments = Seq(
            AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
          )
        )
        eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            report.id,
            Some(user.id),
            Some(OffsetDateTime.now()),
            Constants.EventType.PRO,
            Constants.ActionEvent.CONTACT_EMAIL,
            None,
            Some(stringToDetailsJsValue(s"Notification du professionnel par mail de la réception d'un nouveau signalement ( ${user.email.getOrElse("") } )"))
          )
        ).flatMap(event =>
          reportRepository.update(report.copy(status = Some(TRAITEMENT_EN_COURS)))
        )
      }
      case None => {
        val activationKey = f"${Random.nextInt(1000000)}%06d"
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
        ).map(_ => report)
      }
      case _ => Future(report)
    }
  }

  def newReport(draftReport: Report)(implicit request: play.api.mvc.Request[Any]): Future[Report] =
    for {
      report <- reportRepository.create(
        draftReport.copy(
          id = Some(UUID.randomUUID()),
          creationDate = Some(OffsetDateTime.now()),
          status = Some(if (draftReport.isEligible) Constants.ReportStatus.A_TRAITER else Constants.ReportStatus.NA)
        )
      )
      _ <- reportRepository.attachFilesToReport(report.files.map(_.id), report.id.get)
      files <- reportRepository.retrieveReportFiles(report.id.get)
      report <- {
        mailerService.sendEmail(
          from = mailFrom,
          recipients = configuration.get[Seq[String]]("play.mail.contactRecipients"):_*)(
          subject = "Nouveau signalement",
          bodyHtml = views.html.mails.admin.reportNotification(report, files).toString
        )
        mailerService.sendEmail(
          from = mailFrom,
          recipients = report.email)(
          subject = "Votre signalement",
          bodyHtml = views.html.mails.consumer.reportAcknowledgment(report, files).toString,
          attachments = Seq(
            AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
          )
        )
        if (report.isEligible && report.companySiret.isDefined) notifyProfessionalOfNewReport(report)
        else Future(report)
      }
    } yield report
  
  def updateReport(id: UUID, reportData: Report): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.getReport(id)
      updatedReport <- existingReport match {
        case Some(report) => reportRepository.update(report.copy(
          firstName = report.firstName,
          lastName = report.lastName,
          email = report.email,
          contactAgreement = report.contactAgreement,
          companyName = report.companyName,
          companyAddress = report.companyAddress,
          companyPostalCode = report.companyPostalCode,
          companySiret = report.companySiret
        )).map(Some(_))
        case _ => Future(None)
      }
    } yield {
      updatedReport
        .filter(_.isEligible)
        .filter(_.companySiret.isDefined)
        .flatMap(r => existingReport.filter(_.companySiret != r.companySiret))
        .foreach(notifyProfessionalOfNewReport)
      updatedReport
    }

  def handleReportView(report: Report, user: User): Future[Report] = {
    if (user.userRole == UserRoles.Pro) {
      eventRepository.getEvents(report.id.get, EventFilter(None)).flatMap(events =>
        if(!events.exists(_.action == Constants.ActionEvent.ENVOI_SIGNALEMENT)) {
          manageFirstViewOfReportByPro(report, user.id)
        } else {
          Future(report)
        }
      )
    } else {
      Future(report)
    }
  }

  def addReportFile(id: UUID, filename: String) =
    reportRepository.createFile(ReportFile(id, None, OffsetDateTime.now(), filename))

  def removeReportFile(id: UUID) =
    for {
      repositoryDelete <- reportRepository.deleteFile(id)
      s3Delete <- s3Service.delete(bucketName, id.toString)
    } yield ()

  def deleteReport(id: UUID) =
    for {
      report <- reportRepository.getReport(id)
      _ <- eventRepository.deleteEvents(id)
      _ <- reportRepository.delete(id)
    } yield {
      report.isDefined
    }

  private def manageFirstViewOfReportByPro(report: Report, userUUID: UUID) = {
    for {
      event <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          report.id,
          Some(userUUID),
          Some(OffsetDateTime.now()),
          Constants.EventType.PRO,
          Constants.ActionEvent.ENVOI_SIGNALEMENT,
          None,
          Some(stringToDetailsJsValue("Première consultation du détail du signalement par le professionnel"))
        )
      )
      updatedReport <- report.status match {
        case Some(status) if status.isFinal => Future(report)
        case _ => notifyConsumerOfReportTransmission(report, userUUID)
      }
    } yield updatedReport
  }

  private def notifyConsumerOfReportTransmission(report: Report, userUUID: UUID): Future[Report] = {
    mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = report.email)(
      subject = "Votre signalement",
      bodyHtml = views.html.mails.consumer.reportTransmission(report).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    )
    for {
      event <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          report.id,
          Some(userUUID),
          Some(OffsetDateTime.now()),
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_TRANSMISSION,
          None,
          Some(stringToDetailsJsValue("Envoi email au consommateur d'information de transmission"))
        )
      )
      newReport <- reportRepository.update(report.copy(status = Some(SIGNALEMENT_TRANSMIS)))
    } yield newReport
  }

  private def sendMailsAfterProAcknowledgment(report: Report, reportResponse: ReportResponse, user: User) = {
    user.email.filter(_ != "").foreach(email =>
      mailerService.sendEmail(
        from = mailFrom,
        recipients = email)(
        subject = "Votre réponse au signalement",
        bodyHtml = views.html.mails.professional.reportAcknowledgmentPro(reportResponse, user).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
    )
    mailerService.sendEmail(
      from = mailFrom,
      recipients = report.email)(
      subject = "Le professionnel a répondu à votre signalement",
      bodyHtml = views.html.mails.consumer.reportToConsumerAcknowledgmentPro(report, reportResponse).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    )
    mailerService.sendEmail(
      from = mailFrom,
      recipients = configuration.get[Seq[String]]("play.mail.contactRecipients"):_*)(
      subject = "Un professionnel a répondu à un signalement",
      bodyHtml = views.html.mails.admin.reportToAdminAcknowledgmentPro(report, reportResponse).toString
    )
  }

  def newEvent(reportId: UUID, draftEvent: Event, user: User): Future[Option[Event]] =
    for {
      report <- reportRepository.getReport(reportId)
      newEvent <- report match {
          case Some(r) => eventRepository.createEvent(
            draftEvent.copy(
              id = Some(UUID.randomUUID()),
              creationDate = Some(OffsetDateTime.now()),
              reportId = r.id,
              userId = Some(user.id)
            )).map(Some(_))
          case _ => Future(None)
      }
      updatedReport: Option[Report] <- (report, newEvent) match {
        case (Some(r), Some(event)) => reportRepository.update(
          r.copy(
            status = (event.action, event.resultAction) match {
              case (CONTACT_COURRIER, _)                 => Some(TRAITEMENT_EN_COURS)
              case (_, _)                                => r.status
            })
        ).map(Some(_))
        case _ => Future(None)
      }
    } yield {
      newEvent.foreach(event => event.action match {
        case ENVOI_SIGNALEMENT => notifyConsumerOfReportTransmission(report.get, user.id)
        case _ => ()
      })
      newEvent
    }


  def handleReportResponse(report: Report, reportResponse: ReportResponse, user: User): Future[Report] = {
    logger.debug(s"handleReportResponse ${reportResponse.responseType}")
    for {
      newEvent <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          report.id,
          Some(user.id),
          Some(OffsetDateTime.now()),
          EventType.PRO,
          ActionEvent.REPONSE_PRO_SIGNALEMENT,
          None,
          Some(Json.toJson(reportResponse))
        )
      )
      updatedReport <- reportRepository.update(
        report.copy(
          status = Some(reportResponse.responseType match {
            case ReportResponseType.ACCEPTED => PROMESSE_ACTION
            case ReportResponseType.REJECTED => SIGNALEMENT_INFONDE
            case ReportResponseType.NOT_CONCERNED => SIGNALEMENT_MAL_ATTRIBUE
          })
        )
      )
    } yield {
      sendMailsAfterProAcknowledgment(updatedReport, reportResponse, user)
      updatedReport
    }
  }
}
