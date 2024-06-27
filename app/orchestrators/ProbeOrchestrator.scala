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

  val logger = Logger(getClass)

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
      buildProbe(
        100,
        "reponseconso_probe",
        "Pourcentage de signalements 'Réponse conso'",
        runInterval = 6.hour,
        evaluationPeriod = 12.hour,
        expectedRange = ExpectedRange(min = Some(1), max = Some(40)),
        query = (_, evaluationPeriod) => probeRepository.getReponseConsoPercentage(evaluationPeriod)
      ),
      buildProbe(
        101,
        "lanceur_dalerte_probe",
        "Pourcentage de signalements 'Lanceur d'alerte'",
        runInterval = 6.hour,
        evaluationPeriod = 12.hour,
        expectedRange = ExpectedRange(min = Some(0.1), max = Some(5)),
        query = (_, evaluationPeriod) => probeRepository.getLanceurDalertePercentage(evaluationPeriod)
      ),
      buildProbe(
        102,
        "email_validations_probe",
        "Pourcentage d'emails que les consos ont validés avec succès",
        runInterval = 30.minutes,
        evaluationPeriod = 1.hour,
        expectedRange = ExpectedRange(min = Some(50)),
        query = (_, evaluationPeriod) => probeRepository.getValidatedEmailsPercentage(evaluationPeriod)
      ),
      buildProbeAtLeastOneReport(
        103,
        "number_reports_probe",
        "Nombre de signalements",
        runInterval = 30.minutes,
        evaluationPeriod = 1.hour,
        onlyRunInBusyHours = true
      ),
      buildProbeAtLeastOneReport(
        104,
        "number_reports_with_website_probe",
        "Nombre de signalements sur des sites webs",
        runInterval = 1.hour,
        evaluationPeriod = 1.hour,
        ReportFilter(hasWebsite = Some(true)),
        onlyRunInBusyHours = true
      ),
      buildProbeAtLeastOneReport(
        105,
        "number_reports_with_company_probe",
        "Nombre de signalements avec une société identifiée",
        runInterval = 1.hour,
        evaluationPeriod = 1.hour,
        ReportFilter(hasCompany = Some(true)),
        onlyRunInBusyHours = true
      ),
      buildProbeAtLeastOneReport(
        106,
        "number_reports_with_attachement",
        "Nombre de signalements avec une pièce jointe",
        runInterval = 1.hour,
        evaluationPeriod = 1.hour,
        ReportFilter(hasAttachment = Some(true)),
        onlyRunInBusyHours = true
      ),
      buildProbeAtLeastOneReport(
        107,
        "number_reports_produit_dangereux",
        "Nombre de signalements avec tag ProduitDangereux",
        runInterval = 3.hours,
        evaluationPeriod = 1.day,
        ReportFilter(
          withTags = Seq(ProduitDangereux)
        )
      )
    )
    tasks.foreach(_.schedule())
  }

  private def buildProbeAtLeastOneReport(
      taskId: Int,
      taskName: String,
      description: String,
      runInterval: FiniteDuration,
      evaluationPeriod: FiniteDuration,
      reportFilter: ReportFilter = ReportFilter(),
      onlyRunInBusyHours: Boolean = false
  ): ScheduledTask = buildProbe(
    taskId,
    taskName,
    description,
    runInterval,
    evaluationPeriod,
    query = (dateTime, _) =>
      reportRepository
        .count(
          Some(Admin),
          reportFilter.copy(start = Some(dateTime.minusSeconds(evaluationPeriod.toSeconds)))
        )
        .map(n => Some(n.toDouble)),
    expectedRange = atLeastOne,
    onlyRunInBusyHours
  )

  private def buildProbe(
      taskId: Int,
      taskName: String,
      description: String,
      runInterval: FiniteDuration,
      evaluationPeriod: FiniteDuration,
      query: (OffsetDateTime, FiniteDuration) => Future[Option[Double]],
      expectedRange: ExpectedRange,
      onlyRunInBusyHours: Boolean = false
  ): ScheduledTask = new ScheduledTask(taskId, taskName, taskRepository, actorSystem, taskConfiguration) {
    override val taskSettings = FrequentTaskSettings(runInterval)
    override def runTask() = {
      val now = OffsetDateTime.now
      if (!onlyRunInBusyHours || isDuringTypicalBusyHours(now)) {
        for {
          maybeNumber <- query(now, evaluationPeriod)
          _ <- handleResult(
            description,
            maybeNumber,
            expectedRange,
            evaluationPeriod
          )
        } yield ()
      } else {
        Future.unit
      }
    }
  }

  private def handleResult(
      probeName: String,
      maybeNumber: Option[Double],
      expectedRange: ExpectedRange,
      evaluationPeriod: FiniteDuration
  ) = maybeNumber match {
    case Some(p) if expectedRange.isProblematic(p) =>
      val issueAdjective = if (expectedRange.isTooHigh(p)) "trop haut" else "trop bas"
      logger.warnWithTitle("probe_triggered", s"$probeName est $issueAdjective : $p%")
      for {
        users <- userRepository.listForRoles(Seq(UserRole.Admin))
        _ <- mailService
          .send(
            AdminProbeTriggered
              .Email(users.map(_.email), probeName, p, issueAdjective, evaluationPeriod)
          )
      } yield ()
    case other =>
      logger.info(s"$probeName est correct: $other%")
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
