package orchestrators

import config.TaskConfiguration
import models.UserRole
import orchestrators.ProbeOrchestrator.ExpectedRange
import org.apache.pekko.actor.ActorSystem
import play.api.Logger
import repositories.probe.ProbeRepository
import repositories.tasklock.TaskRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsAdmin.AdminProbeTriggered
import services.emails.MailServiceInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings
import utils.Logs.RichLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class ProbeOrchestrator(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository,
    userRepository: UserRepositoryInterface,
    mailService: MailServiceInterface
)(implicit val executionContext: ExecutionContext) {

  val _Logger = Logger(getClass)

  def scheduleProbeTasks(): Unit = {
    val tasks = Seq(
      new ScheduledTask(100, "reponseconso_probe", taskRepository, actorSystem, taskConfiguration) {
        override val logger       = _Logger
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
        override val logger: Logger = _Logger
        override val taskSettings   = FrequentTaskSettings(interval = 6.hour)
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
        override val logger       = _Logger
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

}
