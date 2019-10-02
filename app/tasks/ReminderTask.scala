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

  // scheduler
  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {
    runTask(LocalDate.now.atStartOfDay())
  }

  /**
    * Traitement principal de la relance.
    *
    * Appelé par le scheduler ou manuellement par les tests
    *
    * @param now date qui représente la date actuelle (now pour le cas nominal, une date fixée pour les tests)
    */
  def runTask(now: LocalDateTime) = {

    logger.debug("Traitement de relance automatique")
    logger.debug(s"taskDate - ${now}");

    val lastWeek = now.minusDays(7)
    val last21Days = now.minusDays(21)

    reportRepository.getReportsForStatusWithUser(TRAITEMENT_EN_COURS).map(reportsWithUser => {
      reportsWithUser.map(tuple => {
        val report = tuple._1
        val userPro = tuple._2
        val userProMail = tuple._2.flatMap(user => user.email)

        eventRepository.getEvents(report.id.get, EventFilter(None, Some(RELANCE))).map(events => {

          events.length match {
            case length if length == 0 => {
              userProMail match {
                case None => {
                  eventRepository.getEvents(report.id.get, EventFilter(None, Some(CONTACT_COURRIER))).map(events => {
                    if (events.length == 0) {
                      logger.debug(s"Cas non prévu de relance reportId ${report.id.get} : pas d'évènement Envoi d'un courrier positionné pour un signalement en Traitement en cours")
                    } else {
                      if (events.head.creationDate.get.toLocalDateTime.isBefore(last21Days)) {
                        runEvent(report, RELANCE, userPro)
                      }
                    }

                  })
                }
                case Some(_) => {
                  eventRepository.getEvents(report.id.get, EventFilter(None, Some(CONTACT_EMAIL))).map(events => {
                    if (events.length == 0) {
                      logger.debug(s"Cas non prévu de relance reportId ${report.id.get} : pas d'évènement Envoi d'un email positionné pour un signalement en Traitement en cours")
                    } else {
                      if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {
                        runEvent(report, RELANCE, userPro)
                      }
                    }
                  })
                }
              }
            }
            case length if length == 1 => {
              userProMail match {
                case None => {
                  if (events.head.creationDate.get.toLocalDateTime.isBefore(last21Days)) {
                    runEvent(report, NON_CONSULTE, userPro)
                  }
                }

                case Some(_) => {
                  if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {
                    runEvent(report, RELANCE, userPro)
                  }
                }
              }
            }

            case length if length >= 2 => {
              if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {
                runEvent(report, NON_CONSULTE, userPro)
              }
            }

          }
        })
      })

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
    })
  }

  def runEvent(report: Report, event: ActionEventValue, userPro: Option[User]) = {

    event match {
      case CONSULTE_IGNORE => manageConsulteIgnore(report)
      case NON_CONSULTE => manageNonConsulte(report)
      case RELANCE => manageRelance(report, userPro.get)
      case _ => Future(None)
    }
  }

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

  def manageRelance(report: Report, proUser: User) = {

    proUser.email match {
      case Some(mail) if mail != "" =>
        eventRepository.createEvent(generateRelanceEvent(report)).map { newEvent =>

          mailerService.sendEmail(
            from = configuration.get[String]("play.mail.from"),
            recipients = mail)(
            subject = "Nouveau signalement",
            bodyHtml = views.html.mails.professional.reportNotification(report).toString,
            attachments = Seq(
              AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
            )
          )

          val debug = (report.firstName, report.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List("professional.reportNotification"))
          logger.debug(s"Résumé de la tâche : ${debug}")
        }

      case None => {
        for {
          newEvent <- eventRepository.createEvent(generateRelanceEvent(report))
          newReport <- reportRepository.update(report.copy(statusPro = Some(A_TRAITER)))
        } yield {

          val debug = (report.firstName, newReport.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List(""))
          logger.debug(s"Résumé de la tâche : ${debug}")

        }
      }
    }
  }

  private def generateRelanceEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    report.id,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    RELANCE,
    None,
    Some(s"Ajout d'un évènement de relance")
  )


}