package orchestrator

import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import controllers.ReportController
import javax.inject.Inject
import models.{Event, Report, User}
import play.api.{Configuration, Environment, Logger}
import repositories.{EventRepository, ReportRepository, UserRepository}
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.StatusPro._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext

class EventOrchestrator @Inject()(reportRepository: ReportRepository,
                                  eventRepository: EventRepository,
                                  userRepository: UserRepository,
                                  mailerService: MailerService,
                                  s3Service: S3Service,
                                  val silhouette: Silhouette[AuthEnv],
                                  val silhouetteAPIKey: Silhouette[APIKeyEnv],
                                  configuration: Configuration,
                                  environment: Environment,
                                  reportController: ReportController) // À supprimer quand le refactor de ReportController sera fait
                                 (implicit val executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  def orchestrateEvent(event: Event) = {

    if (!event.reportId.isDefined) {
      throw new Exception("Pas de reportId dans l'évènement")
    }

    for {
      report <- reportRepository.getReport(event.reportId.get)
    } yield {
      report match {
        case None => throw new Exception("Pas de report associé à l'évènement")
        case Some(nonEmptyReport) => for {
          userPro <- userRepository.findByLogin(nonEmptyReport.companySiret.getOrElse(""))
        } yield {
          userPro match {
            case None => throw new Exception("Pas d'utilisateur valide pour l'évènement")
            case Some(nonEmptyUser) => {
              event.action match {
                case CONSULTE_IGNORE => manageConsulteIgnore(event, nonEmptyReport)
                case NON_CONSULTE => manageNonConsulte(event, nonEmptyReport)
                case RELANCE => manageRelance(event, nonEmptyReport, nonEmptyUser)
              }
            }
          }
        }
      }
    }


  }

  def manageConsulteIgnore(event: Event, report: Report) = {

    for {
      newEvent <- eventRepository.createEvent(
        event.copy(
          id = Some(UUID.randomUUID()),
          creationDate = Some(OffsetDateTime.now()),
        ))
      newReport <- reportRepository.update {
        report.copy(
          statusPro = Some(SIGNALEMENT_CONSULTE_IGNORE)
        )
      }
      mail <- reportController.sendMailClosedByNoAction(newReport)
    } yield {
      (newReport.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List(mail))
    }
  }

  def manageNonConsulte(event: Event, report: Report) = {

    for {
      newEvent <- eventRepository.createEvent(
        event.copy(
          id = Some(UUID.randomUUID()),
          creationDate = Some(OffsetDateTime.now()),
        ))
      newReport <- reportRepository.update {
        report.copy(
          statusPro = Some(SIGNALEMENT_NON_CONSULTE)
        )
      }
      mail <- reportController.sendMailClosedByNoReading(newReport)
    } yield {
      (newReport.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List(mail))
    }
  }


  def manageRelance(event: Event, report: Report, proUser: User) = {

    if (proUser.email.isDefined) {
      for {
        newEvent <- eventRepository.createEvent(
          event.copy(
            id = Some(UUID.randomUUID()),
            creationDate = Some(OffsetDateTime.now()),
          ))
        mail <- reportController.sendMailProfessionalReportNotification(report, proUser)
      } yield {
        logger.debug("Envoi du mail au pro")
        (report.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List(mail))
      }
    } else {
      for {
        newEvent <- eventRepository.createEvent(
          event.copy(
            id = Some(UUID.randomUUID()),
            creationDate = Some(OffsetDateTime.now()),
          ))

        newReport <- reportRepository.update {
          report.copy(
            statusPro = Some(A_TRAITER)
          )
        }
      } yield {
        logger.debug("Changment du status pro à À traiter")
        (newReport.statusPro.map(s => s.value).getOrElse(""), List(newEvent.action.value), List.empty)
      }
    }
  }
}
