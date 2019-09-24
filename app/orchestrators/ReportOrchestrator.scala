package orchestrators

import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID
import play.api.{Configuration, Environment, Logger}
import play.api.libs.mailer.AttachmentFile
import play.mvc.Http.RequestBuilder
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

import models._
import repositories._
import services.{MailerService, S3Service}
import utils.Constants
import utils.Constants.ActionEvent._
import utils.Constants.StatusConso._
import utils.Constants.StatusPro._


class ReportOrchestrator @Inject()(reportRepository: ReportRepository,
                                   eventRepository: EventRepository,
                                   userRepository: UserRepository,
                                   mailerService: MailerService,
                                   s3Service: S3Service,
                                   configuration: Configuration,
                                   environment: Environment)
                                   (implicit val executionContext: ExecutionContext,
                                   implicit val request: play.api.mvc.Request[Any]) {

  val logger = Logger(this.getClass)
  val bucketName = configuration.get[String]("play.buckets.report")
  val mailFrom = configuration.get[String]("play.mail.from")

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

  private def notifyProfessionalOfNewReport(report: Report): Future[_] = {
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
            user.id,
            Some(OffsetDateTime.now()),
            Constants.EventType.PRO,
            Constants.ActionEvent.CONTACT_EMAIL,
            None,
            Some(s"Notification du professionnel par mail de la réception d'un nouveau signalement ( ${user.email.getOrElse("") } )")
          )
        ).map(event =>
          reportRepository.update(
            report.copy(
              statusPro = Some(determineStatusPro(event, report.statusPro)),
              statusConso = Some(determineStatusConso(event, report.statusConso))
            )
          )
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
        )
      }
    }
  }

  def newReport(draftReport: Report) = 
    for {
      report <- reportRepository.create(
        draftReport.copy(
          id = Some(UUID.randomUUID()),
          creationDate = Some(OffsetDateTime.now()),
          statusPro = Some(if (draftReport.isEligible) Constants.StatusPro.A_TRAITER else Constants.StatusPro.NA),
          statusConso = Some(if (draftReport.isEligible) Constants.StatusConso.EN_ATTENTE else Constants.StatusConso.FAIT)
        )
      )
      _ <- reportRepository.attachFilesToReport(report.files.map(_.id), report.id.get)
      files <- reportRepository.retrieveReportFiles(report.id.get)
    } yield {
      mailerService.sendEmail(
        from = mailFrom,
        recipients = configuration.get[String]("play.mail.contactRecipient"))(
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
      report
    }
  
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
        case None => Future(None)
      }
    } yield {
      updatedReport
        .filter(_.isEligible)
        .filter(_.companySiret.isDefined)
        .flatMap(r => existingReport.filter(_.companySiret != r.companySiret))
        .foreach(notifyProfessionalOfNewReport)
      updatedReport
    }
}