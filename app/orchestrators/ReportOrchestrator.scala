package orchestrators

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Named}
import akka.actor.ActorRef
import akka.pattern.ask
import actors.EmailActor
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

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


class ReportOrchestrator @Inject()(reportRepository: ReportRepository,
                                   companyRepository: CompanyRepository,
                                   accessTokenRepository: AccessTokenRepository,
                                   eventRepository: EventRepository,
                                   userRepository: UserRepository,
                                   websiteRepository: WebsiteRepository,
                                   mailerService: MailerService,
                                   @Named("email-actor") emailActor: ActorRef,
                                   s3Service: S3Service,
                                   configuration: Configuration)
                                   (implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  val bucketName = configuration.get[String]("play.buckets.report")
  val mailFrom = configuration.get[EmailAddress]("play.mail.from")
  val tokenDuration = configuration.getOptional[String]("play.tokens.duration").map(java.time.Period.parse(_))

  implicit val timeout: akka.util.Timeout = 5.seconds
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
        emailActor ? EmailActor.EmailRequest(
          from = mailFrom,
          recipients = admins.map(_.email),
          subject = "Nouveau signalement",
          bodyHtml = views.html.mails.professional.reportNotification(report).toString
        )
        val user = admins.head     // We must chose one as Event links to a single User
        eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            Some(report.id),
            Some(company.id),
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
      website <- draftReport.websiteURL.map(websiteRepository.getOrCreate(_).map(Some(_))).getOrElse(Future(None))
      company <- draftReport.companySiret.map(siret => companyRepository.getOrCreate(
        siret,
        Company(
          UUID.randomUUID(),
          siret,
          OffsetDateTime.now,
          draftReport.companyName.get,
          draftReport.companyAddress.get,
          draftReport.companyPostalCode
        )
      ).map(Some(_))).getOrElse(Future(None))
      report <- reportRepository.create(
        draftReport.generateReport.copy(companyId = company.map(_.id), websiteId = website.map(_.id))
      )
      _ <- reportRepository.attachFilesToReport(draftReport.fileIds, report.id)
      files <- reportRepository.retrieveReportFiles(report.id)
      report <- {
        if (report.status == TRAITEMENT_EN_COURS && company.isDefined) notifyProfessionalOfNewReport(report, company.get)
        else Future(report)
      }
    } yield {
      emailActor ? EmailActor.EmailRequest(
        from = mailFrom,
        recipients = configuration.get[List[EmailAddress]]("play.mail.contactRecipients"),
        subject = s"Nouveau signalement [${report.category}]",
        bodyHtml = views.html.mails.admin.reportNotification(report, files).toString
      )
      emailActor ? EmailActor.EmailRequest(
        from = mailFrom,
        recipients = Seq(report.email),
        subject = "Votre signalement",
        bodyHtml = views.html.mails.consumer.reportAcknowledgment(report, files).toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(2)
      )
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
          companyName = Some(reportCompany.name),
          companyAddress = Some(reportCompany.address),
          companyPostalCode = Some(reportCompany.postalCode),
          companySiret = Some(reportCompany.siret)
        )).map(Some(_))
        case _ => Future(None)
      }
      reportWithNewStatus <- reportWithNewData
        .filter(_.companySiret != existingReport.flatMap(_.companySiret))
        .filter(_.creationDate.isAfter(OffsetDateTime.now.minusDays(7)))
        .map(report => reportRepository.update(report.copy(
          status = report.initialStatus()
        )).map(Some(_))).getOrElse(Future(reportWithNewData))
      updatedReport <- reportWithNewStatus
        .filter(_.status == TRAITEMENT_EN_COURS)
        .filter(_.companySiret.isDefined)
        .filter(_.companySiret != existingReport.flatMap(_.companySiret))
        .map(r => notifyProfessionalOfNewReport(r, company).map(Some(_)))
        .getOrElse(Future(reportWithNewStatus))
      _ <- existingReport match {
        case Some(report) => eventRepository.createEvent(
          Event(
            Some(UUID.randomUUID()),
            Some(report.id),
            Some(company.id),
            Some(userUUID),
            Some(OffsetDateTime.now()),
            Constants.EventType.ADMIN,
            Constants.ActionEvent.MODIFICATION_COMMERCANT,
            stringToDetailsJsValue(s"Entreprise précédente : Siret ${report.companySiret.getOrElse("non renseigné")} - ${report.companyAddress.getOrElse("Adresse non renseignée")}")
          )
        ).map(Some(_))
        case _ => Future(None)
      }
      _ <- existingReport.flatMap(_.companyId).map(id => removeAccessToken(id)).getOrElse(Future(Unit))
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
            report.companyId,
            Some(userUUID),
            Some(OffsetDateTime.now()),
            Constants.EventType.ADMIN,
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
      eventRepository.getEvents(None, Some(report.id), EventFilter(None)).flatMap(events =>
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

  private def removeAccessToken(companyId: UUID) = {
    for {
      company <- companyRepository.fetchCompany(companyId)
      reports <- company.map(c => reportRepository.getReports(c.id)).getOrElse(Future(Nil))
      cnt       <- if (reports.isEmpty) accessTokenRepository.removePendingTokens(company.get) else Future(0)
    } yield {
      logger.debug(s"Removed ${cnt} tokens for company ${companyId}")
      Unit
    }
  }

  def deleteReport(id: UUID) =
    for {
      report <- reportRepository.getReport(id)
      _ <- eventRepository.deleteEvents(id)
      _ <- reportRepository.delete(id)
      _ <- report.flatMap(_.companyId).map(id => removeAccessToken(id)).getOrElse(Future(Unit))
    } yield {
      report.isDefined
    }

  private def manageFirstViewOfReportByPro(report: Report, userUUID: UUID) = {
    for {
      event <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
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
    emailActor ? EmailActor.EmailRequest(
      from = mailFrom,
      recipients = Seq(report.email),
      subject = "L'entreprise a pris connaissance de votre signalement",
      bodyHtml = views.html.mails.consumer.reportTransmission(report).toString,
      attachments = mailerService.attachmentSeqForWorkflowStepN(3)
    )
    for {
      event <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
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
      emailActor ? EmailActor.EmailRequest(
        from = mailFrom,
        recipients = Seq(email),
        subject = "Votre réponse au signalement",
        bodyHtml = views.html.mails.professional.reportAcknowledgmentPro(reportResponse, user).toString
      )
    )
    emailActor ? EmailActor.EmailRequest(
      from = mailFrom,
      recipients = Seq(report.email),
      subject = "L'entreprise a répondu à votre signalement",
      bodyHtml = views.html.mails.consumer.reportToConsumerAcknowledgmentPro(
        report,
        reportResponse,
        configuration.get[URI]("play.website.url").resolve(s"/suivi-des-signalements/${report.id}/avis")
      ).toString,
      attachments = mailerService.attachmentSeqForWorkflowStepN(4)
    )
    emailActor ? EmailActor.EmailRequest(
      from = mailFrom,
      recipients = configuration.get[List[EmailAddress]]("play.mail.contactRecipients"),
      subject = s"Un professionnel a répondu à un signalement [${report.category}]",
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
              reportId = Some(r.id),
              companyId = r.companyId,
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

  def handleReportResponse(report: Report, reportResponse: ReportResponse, user: User): Future[Report] = {
    logger.debug(s"handleReportResponse ${reportResponse.responseType}")
    for {
      newEvent <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
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
          updatedReport.companyId,
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


  def handleReportAction(report: Report, reportAction: ReportAction, user: User): Future[Event] = {
    for {
      newEvent <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          Some(user.id),
          Some(OffsetDateTime.now()),
          EventType.fromUserRole(user.userRole),
          reportAction.actionType,
          reportAction.details.map(details => Json.obj("description" -> details)).getOrElse(Json.toJson(reportAction))
        )
      )
      _ <- reportRepository.attachFilesToReport(reportAction.fileIds, report.id)
    } yield {
      logger.debug(s"Create event ${newEvent.id} on report ${report.id} for reportActionType ${reportAction.actionType}")
      newEvent
    }
  }

  def handleReviewOnReportResponse(reportId: UUID, reviewOnReportResponse: ReviewOnReportResponse): Future[Event] = {
    logger.debug(s"Report ${reportId} - the consumer give a review on response")
    eventRepository.createEvent(
      Event(
        Some(UUID.randomUUID()),
        Some(reportId),
        None,
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
}
