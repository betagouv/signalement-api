package tasks


import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetDateTime}
import java.util.UUID

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.{Event, Report, User}
import play.api.libs.mailer.AttachmentFile
import play.api.{Configuration, Environment, Logger}
import repositories.{EventFilter, EventRepository, ReportRepository, UserRepository}
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.EventType.PRO
import utils.Constants.StatusPro._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class ReminderTask @Inject()(actorSystem: ActorSystem,
                             reportRepository: ReportRepository,
                             eventRepository: EventRepository,
                             userRepository: UserRepository,
                             mailerService: MailerService,
                             s3Service: S3Service,
                             val silhouette: Silhouette[AuthEnv],
                             val silhouetteAPIKey: Silhouette[APIKeyEnv],
                             configuration: Configuration,
                             environment: Environment)
                            (implicit val executionContext: ExecutionContext) {


  val logger: Logger = Logger(this.getClass)

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.reminder.start.hour"), configuration.get[Int]("play.tasks.reminder.start.minute"), 0)
  val interval = configuration.get[Int]("play.tasks.reminder.intervalInHours").hours

  val startDate = if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime) else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds


  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {
    logger.debug(s"initialDelay - ${initialDelay}");
    runTask(LocalDate.now.atStartOfDay())
  }

  def runTask(now: LocalDateTime) = {

    logger.debug("Traitement de relance automatique")
    logger.debug(s"taskDate - ${now}");

    for {
      reportsWithUser <- reportRepository.getReportsForStatusWithUser(TRAITEMENT_EN_COURS)
      reportEventsMap <- eventRepository.prefetchReportsEvents(reportsWithUser.map(_._1))
      postReminders <- Future.sequence(
        extractOnGoingReportsToRemindByPost(reportsWithUser, reportEventsMap, now)
          .map(reportWithUser => remindByPost(reportWithUser._1))
      )
      closedByNoReadingForUserWithoutEmail <- Future.sequence(
        extractOnGoingReportsToCloseByNoReadingForUserWithoutEmail(reportsWithUser, reportEventsMap, now)
          .map(reportWithUser => closeReportByNoReadingForUserWithoutEmail(reportWithUser._1))
      )
      mailReminders <- Future.sequence(
        extractOnGoingReportsToRemindByMail(reportsWithUser, reportEventsMap, now)
          .map(reportWithUser => remindByMail(reportWithUser._1, reportWithUser._2.email.get))
      )
      closedByNoReadingForUserWithEmail <- Future.sequence(
        extractOnGoingReportsToCloseByNoReadingForUserWithEmail(reportsWithUser, reportEventsMap, now)
          .map(reportWithUser => closeReportByNoReadingForUserWithEmail(reportWithUser._1))
      )
    } yield {
      postReminders :: closedByNoReadingForUserWithEmail :: mailReminders :: closedByNoReadingForUserWithoutEmail
    }
  }


  def extractEventsWithAction(reportId: UUID, reportEventsMap: Map[UUID, List[Event]], action: ActionEventValue): List[Event] = {
    reportEventsMap.getOrElse(reportId, List.empty).filter(_.action == action)
  }

  def extractOnGoingReportsToRemindByPost(reportsWithUser: List[(Report, User)], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithUser
      .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, RELANCE).length == 0)
      .filterNot(reportWithUser => reportWithUser._2.email.isDefined)
      .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, CONTACT_COURRIER)
        .headOption.flatMap(_.creationDate).map(_.toLocalDateTime.isBefore(now.minusDays(21))).getOrElse(false))
  }

  def remindByPost(report: Report) = {
    for {
      _ <- eventRepository.createEvent(generateReminderEvent(report))
      newEvent <- reportRepository.update(report.copy(statusPro = Some(A_TRAITER)))
    } yield {
      Reminder(report.id, newEvent.id)
    }
  }

  def extractOnGoingReportsToCloseByNoReadingForUserWithoutEmail(reportsWithUser: List[(Report, User)], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithUser
      .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, RELANCE)
        .headOption.flatMap(_.creationDate).map(_.toLocalDateTime.isBefore(now.minusDays(21))).getOrElse(false))
      .filterNot(reportWithUser => reportWithUser._2.email.isDefined)
  }

  def closeReportByNoReadingForUserWithoutEmail(report: Report) = {
    for {
      _ <- eventRepository.createEvent(generateNoReadingEvent(report))
      newEvent <- reportRepository.update(report.copy(statusPro = Some(SIGNALEMENT_NON_CONSULTE)))
    } yield {
      Reminder(report.id, newEvent.id)
    }
  }

  def extractOnGoingReportsToRemindByMail(reportsWithUser: List[(Report, User)], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithUser
      .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, RELANCE).length == 0)
      .filter(reportWithUser => reportWithUser._2.email.isDefined)
      .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, CONTACT_EMAIL)
        .headOption.flatMap(_.creationDate).map(_.toLocalDateTime.isBefore(now.minusDays(7))).getOrElse(false)) :::
      reportsWithUser
        .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, RELANCE).length == 1)
        .filter(reportWithUser => reportWithUser._2.email.isDefined)
        .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, RELANCE)
          .head.creationDate.map(_.toLocalDateTime.isBefore(now.minusDays(7))).getOrElse(false))
  }

  def remindByMail(report: Report, userMail: String) = {
    eventRepository.createEvent(generateReminderEvent(report)).map { newEvent =>
      mailerService.sendEmail(
        from = configuration.get[String]("play.mail.from"),
        recipients = userMail)(
        subject = "Nouveau signalement",
        bodyHtml = views.html.mails.professional.reportNotification(report).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      Reminder(report.id, newEvent.id)
    }
  }

  def extractOnGoingReportsToCloseByNoReadingForUserWithEmail(reportsWithUser: List[(Report, User)], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithUser
      .filter(reportWithUser => extractEventsWithAction(reportWithUser._1.id.get, reportEventsMap, RELANCE)
        .filter(_.creationDate.map(_.toLocalDateTime.isBefore(now.minusDays(7))).getOrElse(false)).length == 2)
      .filter(reportWithUser => reportWithUser._2.email.isDefined)
  }

  def closeReportByNoReadingForUserWithEmail(report: Report) = {
    for {
      _ <- eventRepository.createEvent(generateNoReadingEvent(report))
      newEvent <- reportRepository.update(report.copy(statusPro = Some(SIGNALEMENT_NON_CONSULTE)))
    } yield {
      mailerService.sendEmail(
        from = configuration.get[String]("play.mail.from"),
        recipients = report.email)(
        subject = "Le professionnel n’a pas souhaité consulter votre signalement",
        bodyHtml = views.html.mails.consumer.reportClosedByNoReading(report).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      Reminder(report.id, newEvent.id)
    }
  }

/*

      reportRepository.getReportsForStatusWithUser(SIGNALEMENT_TRANSMIS).map(reportsWithUser => {
        reportsWithUser.map(tuple => {
          val report = tuple._1
          val user = tuple._2

          eventRepository.getEvents(report.id.get, EventFilter(None, Some(RELANCE))).map(events => {

            events.length match {

              case length if length == 0 => eventRepository.getEvents(report.id.get, EventFilter(None, Some(CONTACT_EMAIL))).map(emailEvents => {
                if (emailEvents.length == 0) {
                  logger.debug(s"Cas non prévu de relance reportId ${report.id.get} : pas d'évènement Envoi d'un email positionné pour un signalement transmis")
                } else {
                  if (emailEvents.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                    runEvent(report, RELANCE, user)
                  }
                }
              })
              case length if length == 1 => {
                if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                  runEvent(report, RELANCE, user)
                }
              }
              case length if length >= 2 => {
                if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                  runEvent(report, CONSULTE_IGNORE, user)
                }
              }
            }
          })
        })
      })
    })*/


  def manageConsulteIgnore(report: Report) = {

    for {
      newEvent <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          report.id,
          None,
          Some(OffsetDateTime.now()),
          PRO,
          CONSULTE_IGNORE,
          None,
          Some("Clôture automatique : signalement consulté ignoré")
        )
      )
      newReport <- reportRepository.update(report.copy(statusPro = Some(SIGNALEMENT_CONSULTE_IGNORE)))
    } yield {

      mailerService.sendEmail(
        from = configuration.get[String]("play.mail.from"),
        recipients = report.email)(subject = "Le professionnel n’a pas répondu au signalement",
        bodyHtml = views.html.mails.consumer.reportClosedByNoAction(report).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )

      val debug = (report.firstName, newReport.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List("consumer.reportClosedByNoAction"))
      logger.debug(s"Résumé de la tâche : ${debug}")

    }
  }

  def manageNonConsulte(report: Report) = {

    for {
      newEvent <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          report.id,
          None,
          Some(OffsetDateTime.now()),
          PRO,
          NON_CONSULTE,
          None,
          Some("Clôture automatique : signalement non consulté")
        )
      )
      newReport <- reportRepository.update(report.copy(statusPro = Some(SIGNALEMENT_NON_CONSULTE)))
    } yield {

      mailerService.sendEmail(
        from = configuration.get[String]("play.mail.from"),
        recipients = report.email)(
        subject = "Le professionnel n’a pas souhaité consulter votre signalement",
        bodyHtml = views.html.mails.consumer.reportClosedByNoReading(report).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )

      val debug = (report.firstName, newReport.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List("consumer.reportClosedByNoReading"))
      logger.debug(s"Résumé de la tâche : ${debug}")

    }
  }

  def remindByMail(report: Report, proUser: User) = {
    proUser.email match {
      case Some(mail) if mail != "" =>
        eventRepository.createEvent(generateReminderEvent(report)).map { newEvent =>
          mailerService.sendEmail(
            from = configuration.get[String]("play.mail.from"),
            recipients = mail)(
            subject = "Nouveau signalement",
            bodyHtml = views.html.mails.professional.reportNotification(report).toString,
            attachments = Seq(
              AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
            )
          )
          Reminder(report.id, newEvent.id)
        }

      case None => {
        for {
          _ <- eventRepository.createEvent(generateReminderEvent(report))
          newEvent <- reportRepository.update(report.copy(statusPro = Some(A_TRAITER)))
        } yield {
          Reminder(report.id, newEvent.id)
        }
      }
    }
  }

  private def generateReminderEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    report.id,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    RELANCE,
    None,
    Some(s"Ajout d'un évènement de relance")
  )

  private def generateNoReadingEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    report.id,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    NON_CONSULTE,
    None,
    Some("Clôture automatique : signalement non consulté")
  )

  case class Reminder(
                     reportId: Option[UUID],
                     eventId: Option[UUID]
                     )


}