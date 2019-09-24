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


class RelanceTask @Inject()(actorSystem: ActorSystem,
                            reportRepository: ReportRepository,
                            eventRepository: EventRepository,
                            userRepository: UserRepository,
                            mailerService: MailerService,
                            s3Service: S3Service,
                            val silhouette: Silhouette[AuthEnv],
                            val silhouetteAPIKey: Silhouette[APIKeyEnv],
                            configuration: Configuration,
                            environment: Environment,
                            reportController: ReportController) // TODO: À supprimer
                           (implicit val executionContext: ExecutionContext) {


  val logger: Logger = Logger(this.getClass)

  // user système pour la relance (à mettre en variable d'environnement)
  val systemUuid = "8157c986-de0d-11e9-9d36-2a2ae2dbcce4"


  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.relance.start.hour"), configuration.get[Int]("play.tasks.relance.start.minute"), 0)
  val interval = configuration.get[Int]("play.tasks.relance.interval").minutes

  // val initialDelay = (LocalDateTime.now.until(startTime, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  val initialDelay = 30.seconds

  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {

    logger.debug("Tâche de relance automatique")

    Logger.debug(s"taskDate - ${LocalDate.now}");
    Logger.debug(s"initialDelay - ${initialDelay}");
    Logger.debug(s"interval - ${interval}");

    runTask

  }

  def runTask() = {

    val now = LocalDate.now.atStartOfDay()
    val lastWeek = now.minusDays(7)
    val last21Days = now.minusDays(21)

    reportRepository.getReportsForStatusWithUser(SIGNALEMENT_TRANSMIS).map(reportsWithUser => {
      reportsWithUser.map(tuple => {
        val report = tuple._1
        val user = tuple._2

        eventRepository.getEvents(report.id.get, EventFilter(None, Some(RELANCE))).flatMap(events => {

          events.length match {
            case aux if aux > 2 => {
              if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                runEvent(report, CONSULTE_IGNORE, user)
              }
              Future(None)
            }
            case aux if aux == 1 => {
              if (events.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                runEvent(report, RELANCE, user)
              }
              Future(None)
            }
            case _ => eventRepository.getEvents(report.id.get, EventFilter(None, Some(CONTACT_EMAIL))).flatMap(emailEvents => {
              if (emailEvents.head.creationDate.get.toLocalDateTime.isBefore(lastWeek)) {

                runEvent(report, RELANCE, user)
              }
              Future(None)
            })
          }
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

    if (proUser.email.isDefined) {
      for {
        newEvent <- eventRepository.createEvent(
          createRelanceEvent(report)
        )
        _ <- reportController.sendMailProfessionalReportNotification(report, proUser)
      } yield {

        val debug = (report.id, report.companySiret, List(newEvent.action.value), List("professional.reportNotification"))
        logger.debug(s"Résumé de la tâche : ${debug}")

      }
    } else {
      for {
        newEvent <- eventRepository.createEvent(
          createRelanceEvent(report)
        )

        newReport <- reportRepository.update {
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
