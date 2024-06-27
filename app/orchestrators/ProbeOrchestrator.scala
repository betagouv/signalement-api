package orchestrators

import config.TaskConfiguration
import models.UserRole
import models.UserRole.Admin
import models.report.ReportFilter
import models.report.ReportTag.ProduitDangereux
import orchestrators.ProbeOrchestrator.ExpectedRange
import orchestrators.ProbeOrchestrator.atLeastOne
import orchestrators.ProbeOrchestrator.isDuringTypicalBusyHours
import org.apache.pekko.actor.ActorSystem
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.report.ReportRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsAdmin.AdminProbeTriggered
import services.emails.MailServiceInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings
import utils.Logs.RichLogger

import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class ProbeOrchestrator(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository,
    reportRepository: ReportRepositoryInterface,
    userRepository: UserRepositoryInterface,
    mailService: MailServiceInterface
)(implicit val executionContext: ExecutionContext) {

  val _Logger = Logger(getClass)

  def evaluate() = {

    val step = 1.hour
    iterateDates(start = OffsetDateTime.now.minusDays(10), end = OffsetDateTime.now, step = step)
      .foldLeft(Future.unit) { (previous, offsetDateTime) =>
        if (isDuringTypicalBusyHours(offsetDateTime)) {
          for {
            _ <- previous
            cpt <- reportRepository.count(
              Some(Admin),
              ReportFilter(
                start = Some(offsetDateTime),
                end = Some(offsetDateTime.plusSeconds(step.toSeconds)),
                hasAttachment = Some(true)
              )
            )
            _ = println(s"@@@@ $offsetDateTime => $cpt")
          } yield ()
        } else {
          for {
            _ <- previous
          } yield ()
        }

      }
  }

  private def iterateDates(start: OffsetDateTime, end: OffsetDateTime, step: Duration): Seq[OffsetDateTime] =
    Iterator.iterate(start)(_.plusSeconds(step.toSeconds)).takeWhile(!_.isAfter(end)).toSeq

  def scheduleProbeTasks(): Unit = {
    val tasks = Seq(
      new ScheduledTask(100, "reponseconso_probe", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 6.hours)
        override def runTask(): Future[Unit] = {
          val evaluationPeriod = 12.hours
          for {
            maybePercentage <- probeRepository.getReponseConsoPercentage(evaluationPeriod)
            _ <- handleResult(
              "Pourcentage de signalements 'Réponse conso'",
              maybePercentage,
              ExpectedRange(min = Some(1), max = Some(40)),
              evaluationPeriod
            )
          } yield ()
        }

      },
      new ScheduledTask(101, "lanceur_dalerte_probe", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 6.hour)
        override def runTask(): Future[Unit] = {
          val evaluationPeriod = 12.hours
          for {
            maybePercentage <- probeRepository.getLanceurDalertePercentage(evaluationPeriod)
            _ <- handleResult(
              "Pourcentage de signalements 'Lanceur d'alerte'",
              maybePercentage,
              ExpectedRange(min = Some(0.1), max = Some(5)),
              evaluationPeriod
            )
          } yield ()
        }
      },
      new ScheduledTask(102, "email_validations_probe", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 30.minutes)
        override def runTask(): Future[Unit] = {
          val evaluationPeriod = 1.hour
          for {
            maybePercentage <- probeRepository.getValidatedEmailsPercentage(evaluationPeriod)
            _ <- handleResult(
              "Pourcentage d'emails que les consos ont validés avec succès",
              maybePercentage,
              ExpectedRange(min = Some(50)),
              evaluationPeriod
            )
          } yield ()
        }
      },
      new ScheduledTask(103, "number_reports_probe", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 30.minutes)
        override def runTask(): Future[Unit] = {
          val now = OffsetDateTime.now
          if (isDuringTypicalBusyHours(now)) {
            val evaluationPeriod = 1.hour
            for {
              maybeNumber <- reportRepository.count(
                Some(Admin),
                ReportFilter(start = Some(now.minusSeconds(evaluationPeriod.toSeconds)))
              )
              _ <- handleResult(
                "Nombre de signalements effectués (de tous types)",
                Some(maybeNumber.toDouble),
                atLeastOne,
                evaluationPeriod
              )
            } yield ()
          } else {
            Future.unit
          }
        }
      },
      new ScheduledTask(104, "number_reports_with_website_probe", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 1.hour)
        override def runTask(): Future[Unit] = {
          val now = OffsetDateTime.now
          if (isDuringTypicalBusyHours(now)) {
            val evaluationPeriod = 1.hour
            for {
              maybeNumber <- reportRepository.count(
                Some(Admin),
                ReportFilter(start = Some(now.minusSeconds(evaluationPeriod.toSeconds)), hasWebsite = Some(true))
              )
              _ <- handleResult(
                "Nombre de signalements sur des sites webs",
                Some(maybeNumber.toDouble),
                atLeastOne,
                evaluationPeriod
              )
            } yield ()
          } else {
            Future.unit
          }
        }
      },
      new ScheduledTask(105, "number_reports_with_company_probe", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 12.hour)
        override def runTask(): Future[Unit] = {
          val now = OffsetDateTime.now
          if (isDuringTypicalBusyHours(now)) {
            val evaluationPeriod = 1.hour
            for {
              maybeNumber <- reportRepository.count(
                Some(Admin),
                ReportFilter(start = Some(now.minusSeconds(evaluationPeriod.toSeconds)), hasCompany = Some(true))
              )
              _ <- handleResult(
                "Nombre de signalements avec une société identifiée",
                Some(maybeNumber.toDouble),
                atLeastOne,
                evaluationPeriod
              )
            } yield ()
          } else {
            Future.unit
          }
        }
      },
      new ScheduledTask(106, "number_reports_with_attachement", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 1.hour)
        override def runTask(): Future[Unit] = {
          val now = OffsetDateTime.now
          if (isDuringTypicalBusyHours(now)) {
            val evaluationPeriod = 1.hour
            for {
              maybeNumber <- reportRepository.count(
                Some(Admin),
                ReportFilter(start = Some(now.minusSeconds(evaluationPeriod.toSeconds)), hasAttachment = Some(true))
              )
              _ <- handleResult(
                "Nombre de signalements avec une pièce jointe",
                Some(maybeNumber.toDouble),
                atLeastOne,
                evaluationPeriod
              )
            } yield ()
          } else {
            Future.unit
          }
        }
      },
      new ScheduledTask(107, "number_reports_produit_dangereux", taskRepository, actorSystem, taskConfiguration) {
        override val taskSettings = FrequentTaskSettings(interval = 3.hours)
        override def runTask(): Future[Unit] = {
          val now = OffsetDateTime.now
          if (isDuringTypicalBusyHours(now)) {
            val evaluationPeriod = 1.day
            for {
              maybeNumber <- reportRepository.count(
                Some(Admin),
                ReportFilter(
                  start = Some(now.minusSeconds(evaluationPeriod.toSeconds)),
                  withTags = Seq(ProduitDangereux)
                )
              )
              _ <- handleResult(
                "Nombre de signalements avec tag ProduitDangereux",
                Some(maybeNumber.toDouble),
                atLeastOne,
                evaluationPeriod
              )
            } yield ()
          } else {
            Future.unit
          }
        }
      }
    )
    tasks.foreach(_.schedule())
  }

  private def handleResult(
      probeName: String,
      maybeNumber: Option[Double],
      expectedRange: ExpectedRange,
      evaluationPeriod: FiniteDuration
  ) = maybeNumber match {
    case Some(p) if expectedRange.isProblematic(p) =>
      val issueAdjective = if (expectedRange.isTooHigh(p)) "trop haut" else "trop bas"
      _Logger.warnWithTitle("probe_triggered", s"$probeName est $issueAdjective : $p%")
      for {
        users <- userRepository.listForRoles(Seq(UserRole.Admin))
        _ <- mailService
          .send(
            AdminProbeTriggered
              .Email(users.map(_.email), probeName, p, issueAdjective, evaluationPeriod)
          )
      } yield ()
    case other =>
      _Logger.info(s"$probeName est correct: $other%")
      Future.unit
  }

}

object ProbeOrchestrator {

  case class ExpectedRange(
      min: Option[Double] = None,
      max: Option[Double] = None
  ) {
    def isProblematic(rate: Double): Boolean =
      isTooHigh(rate) || isTooLow(rate)
    def isTooHigh(rate: Double): Boolean =
      max.exists(rate > _)
    private def isTooLow(rate: Double): Boolean =
      min.exists(rate < _)
  }

  val atLeastOne = ExpectedRange(min = Some(1))

  def isDuringTypicalBusyHours(offsetDateTime: OffsetDateTime) = {
    val parisLocalTime = offsetDateTime.atZoneSameInstant(ZoneId.of("Europe/Paris")).toLocalTime
    parisLocalTime.isAfter(LocalTime.of(6, 0)) &&
    parisLocalTime.isBefore(LocalTime.of(22, 0))
  }

}
