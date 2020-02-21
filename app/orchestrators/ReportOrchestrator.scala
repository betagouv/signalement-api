package orchestrators

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.Inject
import models.Event._
import models.ReportResponse._
import models._
import play.api.libs.json.Json
import play.api.{Configuration, Logger}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, EventType}
import utils.{Constants, EmailAddress}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


class ReportOrchestrator @Inject()(reportRepository: ReportRepository,
                                   companyRepository: CompanyRepository,
                                   accessTokenRepository: AccessTokenRepository,
                                   eventRepository: EventRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   s3Service: S3Service,
                                   configuration: Configuration)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val bucketName = configuration.get[String]("play.buckets.report")
  val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))

  implicit val websiteUrl = configuration.get[URI]("play.website.url")

  private def genActivationToken(company: Company, validity: Option[java.time.temporal.TemporalAmount]): Future[String] =
    for {
      existingToken <- accessTokenRepository.fetchActivationToken(company)
      _             <- existingToken.map(accessTokenRepository.updateToken(_, AccessLevel.ADMIN, tokenDuration)).getOrElse(Future(None))
      token         <- existingToken.map(Future(_)).getOrElse(
                        accessTokenRepository.createToken(
                          TokenKind.COMPANY_INIT, f"${Random.nextInt(1000000)}%06d", tokenDuration,
                          Some(company), Some(AccessLevel.ADMIN)
                        )
                       )
    } yield token.token

  private def notifyProfessionalOfNewReport(report: Report, company: Company): Future[Report] = {
    companyRepository.fetchAdmins(company).flatMap(admins => {
      if (admins.nonEmpty) {
        mailerService.sendEmail(
          from = mailFrom,
          recipients = admins.map(_.email): _*)(
          subject = "Nouveau signalement",
          bodyHtml = views.html.mails.professional.reportNotification(report).toString
        )
        val user = admins.head     // We must chose one as Event links to a single User
        eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            Some(report.id),
            Some(user.id),
            Some(OffsetDateTime.now()),
            Constants.EventType.PRO,
            Constants.ActionEvent.CONTACT_EMAIL,
            stringToDetailsJsValue(s"Notification du professionnel par mail de la réception d'un nouveau signalement ( ${admins.map(_.email).mkString(", ")} )")
          )
        ).flatMap(event =>
          reportRepository.update(report.copy(status = TRAITEMENT_EN_COURS))
        )
      } else {
        genActivationToken(company, tokenDuration).map(_ => report)
      }
    })
  }

  def newReport(draftReport: DraftReport)(implicit request: play.api.mvc.Request[Any]): Future[Report] =
    for {
      company <- companyRepository.getOrCreate(
        draftReport.companySiret,
        Company(
          UUID.randomUUID(),
          draftReport.companySiret,
          OffsetDateTime.now,
          draftReport.companyName,
          draftReport.companyAddress,
          Some(draftReport.companyPostalCode)
        )
      )
      report <- reportRepository.create(draftReport.generateReport.copy(companyId = Some(company.id)))
      _ <- reportRepository.attachFilesToReport(draftReport.fileIds, report.id)
      files <- reportRepository.retrieveReportFiles(report.id)
      report <- {
        mailerService.sendEmail(
          from = mailFrom,
          recipients = configuration.get[List[EmailAddress]]("play.mail.contactRecipients"):_*)(
          subject = s"Nouveau signalement [${report.category}]",
          bodyHtml = views.html.mails.admin.reportNotification(report, files).toString
        )
        mailerService.sendEmail(
          from = mailFrom,
          recipients = report.email)(
          subject = "Votre signalement",
          bodyHtml = views.html.mails.consumer.reportAcknowledgment(report, files).toString,
          mailerService.attachmentSeqForWorkflowStepN(2)
        )
        if (report.status == A_TRAITER && report.companySiret.isDefined) notifyProfessionalOfNewReport(report, company)
        else Future(report)
      }
    } yield {
      logger.debug(s"Report ${report.id} created")
      report
    }

  def updateReportCompany(reportId: UUID, reportCompany: ReportCompany, userUUID: UUID): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.getReport(reportId)
      company <- companyRepository.getOrCreate(
        reportCompany.siret,
        Company(
          UUID.randomUUID(),
          reportCompany.siret,
          OffsetDateTime.now,
          reportCompany.name,
          reportCompany.address,
          Some(reportCompany.postalCode)
        )
      )
      reportWithNewData <- existingReport match {
        case Some(report) => reportRepository.update(report.copy(
          companyId = Some(company.id),
          companyName = reportCompany.name,
          companyAddress = reportCompany.address,
          companyPostalCode = Some(reportCompany.postalCode),
          companySiret = Some(reportCompany.siret)
        )).map(Some(_))
        case _ => Future(None)
      }
      reportWithNewStatus <- reportWithNewData
        .filter(_.companySiret != existingReport.flatMap(_.companySiret))
        .map(report => reportRepository.update(report.copy(
          status = report.initialStatus()
        )).map(Some(_))).getOrElse(Future(reportWithNewData))
      updatedReport <- reportWithNewStatus
        .filter(_.status == A_TRAITER)
        .filter(_.companySiret.isDefined)
        .filter(_.companySiret != existingReport.flatMap(_.companySiret))
        .map(r => notifyProfessionalOfNewReport(r, company).map(Some(_)))
        .getOrElse(Future(reportWithNewStatus))
      _ <- existingReport match {
        case Some(report) => eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            Some(report.id),
            Some(userUUID),
            Some(OffsetDateTime.now()),
            Constants.EventType.RECTIF,
            Constants.ActionEvent.MODIFICATION_COMMERCANT,
            stringToDetailsJsValue(s"Entreprise précédente : Siret ${report.companySiret.getOrElse("non renseigné")} - ${report.companyAddress}")
          )
        ).map(Some(_))
        case _ => Future(None)
      }
    } yield updatedReport

  def updateReportConsumer(reportId: UUID, reportConsumer: ReportConsumer, userUUID: UUID): Future[Option[Report]] =
    for {
      existingReport <- reportRepository.getReport(reportId)
      updatedReport <- existingReport match {
        case Some(report) => reportRepository.update(report.copy(
          firstName = reportConsumer.firstName,
          lastName = reportConsumer.lastName,
          email = reportConsumer.email,
          contactAgreement = reportConsumer.contactAgreement
        )).map(Some(_))
        case _ => Future(None)
      }
      _ <- existingReport match {
        case Some(report) => eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            Some(report.id),
            Some(userUUID),
            Some(OffsetDateTime.now()),
            Constants.EventType.RECTIF,
            Constants.ActionEvent.MODIFICATION_CONSO,
            stringToDetailsJsValue(
              s"Consommateur précédent : ${report.firstName} ${report.lastName} - ${report.email} " +
                s"- Accord pour contact : ${if (report.contactAgreement) "oui" else "non"}"
            )
          )
        ).map(Some(_))
        case _ => Future(None)
      }
    } yield updatedReport

  def handleReportView(report: Report, user: User): Future[Report] = {
    if (user.userRole == UserRoles.Pro) {
      eventRepository.getEvents(report.id, EventFilter(None)).flatMap(events =>
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

  def addReportFile(filename: String, storageFilename: String, origin: ReportFileOrigin) = {
    reportRepository.createFile(
      ReportFile(
        UUID.randomUUID,
        None,
        OffsetDateTime.now(),
        filename,
        storageFilename,
        origin
      )
    )
  }

  def removeReportFile(id: UUID) =
    for {
      reportFile <- reportRepository.getFile(id)
      repositoryDelete <- reportFile.map(f => reportRepository.deleteFile(f.id)).getOrElse(Future(None))
      s3Delete <- reportFile.map(f => s3Service.delete(bucketName, f.storageFilename)).getOrElse(Future(None))
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
          Some(report.id),
          Some(userUUID),
          Some(OffsetDateTime.now()),
          Constants.EventType.PRO,
          Constants.ActionEvent.ENVOI_SIGNALEMENT,
          stringToDetailsJsValue("Première consultation du détail du signalement par le professionnel")
        )
      )
      updatedReport <-
        if (report.status.isFinal) {
          Future(report)
        } else {
          notifyConsumerOfReportTransmission(report, userUUID)
        }
    } yield updatedReport
  }

  private def notifyConsumerOfReportTransmission(report: Report, userUUID: UUID): Future[Report] = {
    mailerService.sendEmail(
      from = mailFrom,
      recipients = report.email)(
      subject = "L'entreprise a pris connaissance de votre signalement",
      bodyHtml = views.html.mails.consumer.reportTransmission(report).toString,
      mailerService.attachmentSeqForWorkflowStepN(3)
    )
    for {
      event <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          Some(userUUID),
          Some(OffsetDateTime.now()),
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_TRANSMISSION,
          stringToDetailsJsValue("Envoi email au consommateur d'information de transmission")
        )
      )
      newReport <- reportRepository.update(report.copy(status = SIGNALEMENT_TRANSMIS))
    } yield newReport
  }

  private def sendMailsAfterProAcknowledgment(report: Report, reportResponse: ReportResponse, user: User) = {
    Some(user.email).filter(_.value != "").foreach(email =>
      mailerService.sendEmail(
        from = mailFrom,
        recipients = email)(
        subject = "Votre réponse au signalement",
        bodyHtml = views.html.mails.professional.reportAcknowledgmentPro(reportResponse, user).toString
      )
    )
    mailerService.sendEmail(
      from = mailFrom,
      recipients = report.email)(
      subject = "L'entreprise a répondu à votre signalement",
      bodyHtml = views.html.mails.consumer.reportToConsumerAcknowledgmentPro(
        report,
        reportResponse,
        configuration.get[URI]("play.website.url").resolve(s"/suivi-des-signalements/${report.id}/avis")
      ).toString,
      mailerService.attachmentSeqForWorkflowStepN(4)
    )
    mailerService.sendEmail(
      from = mailFrom,
      recipients = configuration.get[List[EmailAddress]]("play.mail.contactRecipients"):_*)(
      subject = s"Un professionnel a répondu à un signalement [${report.category}]",
      bodyHtml = views.html.mails.admin.reportToAdminAcknowledgmentPro(report, reportResponse).toString
    )
  }

  def newEvent(reportId: UUID, draftEvent: Event, user: User): Future[Option[Event]] =
    for {
      report <- reportRepository.getReport(reportId)
      newEvent <- report match {
          case Some(r) if authorizedEventForReport(draftEvent, r ) => eventRepository.createEvent(
            draftEvent.copy(
              id = Some(UUID.randomUUID()),
              creationDate = Some(OffsetDateTime.now()),
              reportId = Some(r.id),
              userId = Some(user.id)
            )).map(Some(_))
          case _ => Future(None)
      }
      updatedReport: Option[Report] <- (report, newEvent) match {
        case (Some(r), Some(event)) => reportRepository.update(
          r.copy(
            status = event.action match {
              case CONTACT_COURRIER => TRAITEMENT_EN_COURS
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


  //TODO complete this function in a specific PullRequest to securised the workflow
  def authorizedEventForReport(event: Event, report: Report): Boolean = {
    (event.action, report.status) match {
      case (CONTACT_COURRIER, A_TRAITER) => true
      case (CONTACT_COURRIER, _) => false
      case (_, _) => true
    }
  }


  def handleReportResponse(report: Report, reportResponse: ReportResponse, user: User): Future[Report] = {
    logger.debug(s"handleReportResponse ${reportResponse.responseType}")
    for {
      newEvent <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          Some(user.id),
          Some(OffsetDateTime.now()),
          EventType.PRO,
          ActionEvent.REPONSE_PRO_SIGNALEMENT,
          Json.toJson(reportResponse)
        )
      )
      _ <- reportRepository.attachFilesToReport(reportResponse.fileIds, report.id)
      updatedReport <- reportRepository.update(
        report.copy(
          status = reportResponse.responseType match {
            case ReportResponseType.ACCEPTED => PROMESSE_ACTION
            case ReportResponseType.REJECTED => SIGNALEMENT_INFONDE
            case ReportResponseType.NOT_CONCERNED => SIGNALEMENT_MAL_ATTRIBUE
          }
        )
      )
      - <- Future(sendMailsAfterProAcknowledgment(updatedReport, reportResponse, user))
      - <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          Some(user.id),
          Some(OffsetDateTime.now()),
          Constants.EventType.CONSO,
          Constants.ActionEvent.EMAIL_REPONSE_PRO,
          stringToDetailsJsValue("Envoi email au consommateur de la réponse du professionnel")
        )
      )
    } yield {
      updatedReport
    }
  }

  def handleReviewOnReportResponse(reportId: UUID, reviewOnReportResponse: ReviewOnReportResponse): Future[Event] = {
    logger.debug(s"Report ${reportId} - the consumer give a review on response")
    eventRepository.createEvent(
      Event(
        Some(UUID.randomUUID()),
        Some(reportId),
        None,
        Some(OffsetDateTime.now()),
        EventType.CONSO,
        ActionEvent.REVIEW_ON_REPORT_RESPONSE,
        stringToDetailsJsValue(
          s"${if (reviewOnReportResponse.positive) "Avis positif" else "Avis négatif"}" +
            s"${reviewOnReportResponse.details.map(d => s" - $d").getOrElse("")}"
        )
      )
    )
  }

  def markBatchPosted(user: User, reportsIds: List[UUID]): Future[Unit] = {
    for {
      contactedCompanies  <- reportRepository.getReportsByIds(reportsIds).map(_.flatMap(_.companyId).distinct)
      pendingReports      <- reportRepository.getPendingReports(contactedCompanies)
      eventsMap           <- eventRepository.prefetchReportsEvents(pendingReports)
      _                   <- Future.sequence(pendingReports.filter(r =>
                                !eventsMap.getOrElse(r.id, List.empty).exists(_.action == RELANCE) || reportsIds.contains(r.id)).map(r =>
          newEvent(
            r.id,
            Event(Some(UUID.randomUUID()), Some(r.id), Some(user.id), Some(OffsetDateTime.now), EventType.PRO, ActionEvent.CONTACT_COURRIER, Json.obj()),
            user
          )
        ))
    } yield Unit
  }
}
