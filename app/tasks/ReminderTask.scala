package tasks


import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import controllers.ReportController
import models.{Event, User}
import repositories.{EventFilter, EventRepository, ReportRepository, UserRepository}
import services.S3Service
import utils.Constants.ActionEvent._
import utils.Constants.StatusPro._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import java.time.{LocalDate, LocalTime, OffsetDateTime}

import akka.actor.ActorSystem
import javax.inject.Inject
import models.Report
import play.api.{Configuration, Environment, Logger}
import services.MailerService
import utils.Constants.EventType.PRO

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
                             environment: Environment,
                             reportController: ReportController) // TODO: À supprimer quand les méthodes sendMail seront déplacées
                            (implicit val executionContext: ExecutionContext) {


  val logger: Logger = Logger(this.getClass)

  val systemUuid = configuration.get[String]("play.systemUuid")

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.reminder.start.hour"), configuration.get[Int]("play.tasks.reminder.start.minute"), 0)
  val interval = configuration.get[Int]("play.tasks.reminder.interval").minutes

  // val initialDelay = (LocalDateTime.now.until(startTime, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds
  val initialDelay = 30.seconds

  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {

    logger.debug("Tâche de relance automatique")

    logger.debug(s"taskDate - ${LocalDate.now}");
    logger.debug(s"initialDelay - ${initialDelay}");
    logger.debug(s"interval - ${interval}");

    runTask

  }

  def runTask() = {

    val now = LocalDate.now.atStartOfDay()
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
                    if (events.head.creationDate.get.toLocalDateTime.isBefore(last21Days)) {
                      runEvent(report, RELANCE, userPro)
                    } else {
                      logger.debug(s"Cas non prévu de relance reportId ${report.id.get} : pas d'évènement Envoi d'un courrier positionné")
                    }

                  })
                }
                case Some(_) => {
                  eventRepository.getEvents(report.id.get, EventFilter(None, Some(CONTACT_EMAIL))).map(events => {
                    if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {
                      runEvent(report, RELANCE, userPro)
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
              case aux if aux > 2 => {
                if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                  runEvent(report, CONSULTE_IGNORE, user)
                }
              }
              case aux if aux == 1 => {
                if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                  runEvent(report, RELANCE, user)
                }
              }
              case _ => eventRepository.getEvents(report.id.get, EventFilter(None, Some(CONTACT_EMAIL))).map(emailEvents => {
                if (emailEvents.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                  runEvent(report, RELANCE, user)
                }
              })
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
      newEvent <- eventRepository.createEvent(Event(
        Some(UUID.randomUUID()),
        report.id,
        UUID.fromString(systemUuid),
        Some(OffsetDateTime.now()),
        PRO,
        CONSULTE_IGNORE,
        None,
        Some("Clôture automatique : signalement consulté ignoré"))
      )
      newReport <- reportRepository.update {
        report.copy(
          statusPro = Some(SIGNALEMENT_CONSULTE_IGNORE)
        )
      }
      _ <- reportController.sendMailClosedByNoAction(newReport)

    } yield {

      val debug = (report.id, report.companySiret, List(newEvent.action.value), List("consumer.reportClosedByNoAction"))
      logger.debug(s"Résumé de la tâche : ${debug}")

    }
  }

  def manageNonConsulte(report: Report) = {

    for {
      newEvent <- eventRepository.createEvent(Event(
        Some(UUID.randomUUID()),
        report.id,
        UUID.fromString(systemUuid),
        Some(OffsetDateTime.now()),
        PRO,
        NON_CONSULTE,
        None,
        Some("Clôture automatique : signalement non consulté"))
      )
      newReport <- reportRepository.update {
        report.copy(
          statusPro = Some(SIGNALEMENT_NON_CONSULTE)
        )
      }
      _ <- reportController.sendMailClosedByNoReading(newReport)
    } yield {

      val debug = (report.id, report.companySiret, List(newEvent.action.value), List("consumer.reportClosedByNoReading"))
      logger.debug(s"Résumé de la tâche : ${debug}")

    }
  }

  def manageRelance(report: Report, proUser: User) = {

    proUser.email match {
      case Some(_) => {
        for {
          newEvent <- eventRepository.createEvent(
            createRelanceEvent(report)
          )
          _ <- reportController.sendMailProfessionalReportNotification(report, proUser)
        } yield {

          val debug = (report.id, report.companySiret, List(newEvent.action.value), List("professional.reportNotification"))
          logger.debug(s"Résumé de la tâche : ${debug}")

        }
      }
      case None => {
        for {
          newEvent <- eventRepository.createEvent(
            createRelanceEvent(report)
          )

          _ <- reportRepository.update {
            report.copy(
              statusPro = Some(A_TRAITER)
            )
          }
        } yield {

          val debug = (report.id, report.companySiret, List(newEvent.action.value), List(""))
          logger.debug(s"Résumé de la tâche : ${debug}")

        }
      }
    }
  }

  private def createRelanceEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    report.id,
    UUID.fromString(systemUuid),
    Some(OffsetDateTime.now()),
    PRO,
    RELANCE,
    None,
    Some(s"Ajout d'un évènement de relance")
  )


}