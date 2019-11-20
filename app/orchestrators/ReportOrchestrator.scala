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
import utils.{Constants, EmailAddress, EmailAddressList}
import utils.Constants.ActionEvent._
import utils.Constants.{ActionEvent, EventType}
import utils.Constants.ReportStatus._


class ReportOrchestrator @Inject()(reportRepository: ReportRepository,
                                   companyRepository: CompanyRepository,
                                   companyAccessRepository: CompanyAccessRepository,
                                   eventRepository: EventRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   s3Service: S3Service,
                                   configuration: Configuration,
                                   environment: Environment)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val bucketName = configuration.get[String]("play.buckets.report")
  val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))


  private def notifyProfessionalOfNewReport(report: Report, company: Company): Future[Report] = {
    companyAccessRepository.fetchAdmins(company).flatMap(admins => {
      val adminsWithEmail = admins.filter(_.email.isDefined)
      if (adminsWithEmail.nonEmpty) {
        mailerService.sendEmail(
          from = mailFrom,
          recipients = adminsWithEmail.flatMap(_.email): _*)(
          subject = "Nouveau signalement",
          bodyHtml = views.html.mails.professional.reportNotification(report).toString,
          attachments = Seq(
            AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
          )
        )
        val user = adminsWithEmail.head     // We must chose one as Event links to a single User
        eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            report.id,
            Some(user.id),
            Some(OffsetDateTime.now()),
            Constants.EventType.PRO,
            Constants.ActionEvent.CONTACT_EMAIL,
            stringToDetailsJsValue(s"Notification du professionnel par mail de la réception d'un nouveau signalement ( ${user.email.getOrElse("") } )")
          )
        ).flatMap(event =>
          reportRepository.update(report.copy(status = Some(TRAITEMENT_EN_COURS)))
        )
      } else if (admins.isEmpty) {
        companyAccessRepository.createToken(company, AccessLevel.ADMIN, f"${Random.nextInt(1000000)}%06d", tokenDuration).map(_ => report)
      } else {
        Future(report)
      }
    })
  }

  def newReport(draftReport: Report)(implicit request: play.api.mvc.Request[Any]): Future[Report] =
    for {
      company <- draftReport.companySiret.map(siret => companyRepository.getOrCreate(
        siret,
        Company(
          UUID.randomUUID(),
          siret,
          OffsetDateTime.now,
          draftReport.companyName,
          draftReport.companyAddress,
          draftReport.companyPostalCode
        )
      ).map(Some(_))).getOrElse(Future(None))
      report <- reportRepository.create(
        draftReport.copy(
          id = Some(UUID.randomUUID()),
          companyId = company.map(_.id),
          creationDate = Some(OffsetDateTime.now()),
          status = Some(if (draftReport.isEligible) Constants.ReportStatus.A_TRAITER else Constants.ReportStatus.NA)
        )
      )
      _ <- reportRepository.attachFilesToReport(report.files.map(_.id), report.id.get)
      files <- reportRepository.retrieveReportFiles(report.id.get)
      report <- {
        mailerService.sendEmail(
          from = mailFrom,
          recipients = configuration.get[EmailAddressList]("play.mail.contactRecipients").value:_*)(
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
        if (report.isEligible && report.companySiret.isDefined) notifyProfessionalOfNewReport(report, company.get)
        else Future(report)
      }
    } yield report
  
  def updateReport(id: UUID, reportData: Report): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.getReport(id)
      company <- reportData.companySiret.map(siret => companyRepository.getOrCreate(
        siret,
        Company(
          UUID.randomUUID(),
          siret,
          OffsetDateTime.now,
          reportData.companyName,
          reportData.companyAddress,
          reportData.companyPostalCode
        )
      ).map(Some(_))).getOrElse(Future(None))
      reportWithNewData <- existingReport match {
        case Some(report) => reportRepository.update(report.copy(
          firstName = reportData.firstName,
          lastName = reportData.lastName,
          email = reportData.email,
          contactAgreement = reportData.contactAgreement,
          companyId = company.map(_.id),
          companyName = reportData.companyName,
          companyAddress = reportData.companyAddress,
          companyPostalCode = reportData.companyPostalCode,
          companySiret = reportData.companySiret
        )).map(Some(_))
        case _ => Future(None)
      }
      updatedReport <- reportWithNewData
          .filter(_.isEligible)
          .filter(_.companySiret.isDefined)
          .filter(_.companySiret != existingReport.flatMap(_.companySiret))
          .map(r => notifyProfessionalOfNewReport(r, company.get).map(Some(_)))
          .getOrElse(Future(reportWithNewData))
    } yield updatedReport

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

  def addReportFile(id: UUID, filename: String, origin: ReportFileOrigin) =
    reportRepository.createFile(
      ReportFile(
        id,
        None,
        OffsetDateTime.now(),
        filename,
        origin
      )
    )

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
          stringToDetailsJsValue("Première consultation du détail du signalement par le professionnel")
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
      from = mailFrom,
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
          stringToDetailsJsValue("Envoi email au consommateur d'information de transmission")
        )
      )
      newReport <- reportRepository.update(report.copy(status = Some(SIGNALEMENT_TRANSMIS)))
    } yield newReport
  }

  private def sendMailsAfterProAcknowledgment(report: Report, reportResponse: ReportResponse, user: User) = {
    user.email.filter(_.value != "").foreach(email =>
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
      recipients = configuration.get[EmailAddressList]("play.mail.contactRecipients").value:_*)(
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
            status = event.action match {
              case CONTACT_COURRIER => Some(TRAITEMENT_EN_COURS)
              case _ => r.status
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
          Json.toJson(reportResponse)
        )
      )
      _ <- reportRepository.attachFilesToReport(reportResponse.fileIds, report.id.get)
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
