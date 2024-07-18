package orchestrators

import config.TaskConfiguration
import models.EmailValidationFilter
import models.UserRole
import models.UserRole.Admin
import models.auth.AuthAttemptFilter
import models.report.ReportFileOrigin.Consumer
import models.report.ReportFileOrigin.Professional
import models.report.ReportFilter
import models.report.ReportStatus.InformateurInterne
import models.report.ReportTag.ProduitDangereux
import orchestrators.ProbeOrchestrator.ExpectedRange
import orchestrators.ProbeOrchestrator.OffsetDateTimeOps
import orchestrators.ProbeOrchestrator.atLeastOne
import orchestrators.ProbeOrchestrator.isDuringTypicalBusyHours
import org.apache.pekko.actor.ActorSystem
import play.api.Logger
import repositories.authattempt.AuthAttemptRepositoryInterface
import repositories.emailvalidation.EmailValidationRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.probe.ProbeRepository
import repositories.report.ReportRepositoryInterface
import repositories.reportfile.ReportFileFilter
import repositories.reportfile.ReportFileRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsAdmin.AdminProbeTriggered
import services.emails.MailServiceInterface
import tasks.ScheduledTask
import tasks.model.TaskSettings.FrequentTaskSettings
import utils.Constants.ActionEvent._
import utils.EmailAddress
import utils.Logs.RichLogger

import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class ProbeOrchestrator(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    probeRepository: ProbeRepository,
    reportRepository: ReportRepositoryInterface,
    userRepository: UserRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    authAttemptRepository: AuthAttemptRepositoryInterface,
    reportFileRepository: ReportFileRepositoryInterface,
    emailValidationRepository: EmailValidationRepositoryInterface,
    mailService: MailServiceInterface
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(getClass)

  // Helper to evaluate the results of a given query on historical data on DB anon.
  // Might be helpful when creating new probe or adjusting their parameters
  @scala.annotation.nowarn
  private def evaluateProbeQuery(
      runInterval: FiniteDuration,
      evaluationPeriod: FiniteDuration,
      query: (OffsetDateTime, FiniteDuration) => Future[Option[Double]]
  ): Unit = {
    val now       = OffsetDateTime.now
    val start     = now.minusDays(10)
    val end       = now.minusDays(2)
    val dateTimes = Iterator.iterate(start)(_.plusSeconds(runInterval.toSeconds)).takeWhile(!_.isAfter(end)).toSeq
    dateTimes.foldLeft(Future.unit) { (previous, dateTime) =>
      for {
        _   <- previous
        cpt <- query(dateTime, evaluationPeriod)
        busyLabel = if (isDuringTypicalBusyHours(dateTime)) " [busy hours]" else ""
        _         = logger.info(s"Evaluating probe query at $dateTime$busyLabel => $cpt")
      } yield ()
    }: Unit
  }

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
        "informateurinterne_probe",
        "Pourcentage de signalements 'Informateur interne'",
        runInterval = 6.hour,
        evaluationPeriod = 12.hour,
        expectedRange = ExpectedRange(min = Some(0.1), max = Some(25)),
        query = (dateTime, evaluationPeriod) => {
          val filter = ReportFilter(
            start = Some(dateTime.minusDuration(evaluationPeriod)),
            end = Some(dateTime)
          )
          for {
            nb      <- reportRepository.count(Some(Admin), filter.copy(status = Seq(InformateurInterne)))
            nbTotal <- reportRepository.count(Some(Admin), filter)
          } yield computePercentage(nb, nbTotal)
        }
      ),
      buildProbe(
        102,
        "email_validations_probe",
        "Pourcentage d'emails que les consos ont validés avec succès",
        runInterval = 30.minutes,
        evaluationPeriod = 1.hour,
        expectedRange = ExpectedRange(min = Some(60)),
        query = (dateTime, evaluationPeriod) => {
          val filter = EmailValidationFilter(
            start = Some(dateTime.minusDuration(evaluationPeriod)),
            end = Some(dateTime)
          )
          for {
            nb      <- emailValidationRepository.count(filter.copy(validated = Some(true)))
            nbTotal <- emailValidationRepository.count(filter)
          } yield computePercentage(nb, nbTotal)

        }
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
      ),
      buildProbe(
        108,
        "courrieractivation_probe",
        "Nombre d'envois de courriers d'activation",
        runInterval = 24.hour,
        evaluationPeriod = 14.days,
        expectedRange = ExpectedRange(min = Some(100), max = Some(3000)),
        query = (dateTime, evaluationPeriod) => countEvents(POST_ACCOUNT_ACTIVATION_DOC, dateTime, evaluationPeriod)
      ),
      buildProbe(
        109,
        "courrierrelance_probe",
        "Nombre d'envois de courriers de relance",
        runInterval = 7.days,
        evaluationPeriod = 60.days,
        expectedRange = ExpectedRange(min = Some(50), max = Some(2000)),
        query = (dateTime, evaluationPeriod) => countEvents(POST_FOLLOW_UP_DOC, dateTime, evaluationPeriod)
      ),
      buildProbe(
        110,
        "accountactivation_probe",
        "Nombre d'activations de compte des pros",
        runInterval = 6.hour,
        evaluationPeriod = 1.day,
        expectedRange = ExpectedRange(min = Some(3), max = Some(300)),
        query = (dateTime, evaluationPeriod) => countEvents(ACCOUNT_ACTIVATION, dateTime, evaluationPeriod)
      ),
      buildProbe(
        111,
        "reportreading_probe",
        "Nombre de premières lectures d'un signalement par les pros",
        runInterval = 1.hour,
        evaluationPeriod = 6.hours,
        expectedRange = ExpectedRange(min = Some(1), max = Some(700)),
        query = (dateTime, evaluationPeriod) => countEvents(REPORT_READING_BY_PRO, dateTime, evaluationPeriod),
        onlyRunInBusyHours = true
      ),
      buildProbe(
        112,
        "reportresponses_probe",
        "Nombre de réponses des pros",
        runInterval = 6.hour,
        evaluationPeriod = 12.hours,
        expectedRange = ExpectedRange(min = Some(5), max = Some(1000)),
        query = (dateTime, evaluationPeriod) => countEvents(REPORT_PRO_RESPONSE, dateTime, evaluationPeriod)
      ),
      buildProbe(
        113,
        "engagementhonoured_probe",
        "Nombre d'engagements marqués comme honoré par les pros",
        runInterval = 1.day,
        evaluationPeriod = 7.days,
        expectedRange = ExpectedRange(min = Some(1), max = Some(500)),
        query = (dateTime, evaluationPeriod) => countEvents(REPORT_PRO_ENGAGEMENT_HONOURED, dateTime, evaluationPeriod)
      ),
      buildProbe(
        114,
        "responsereview_probe",
        "Nombre de reviews initiale des consos sur la réponse des pros",
        runInterval = 4.hours,
        evaluationPeriod = 8.hours,
        expectedRange = ExpectedRange(min = Some(1), max = Some(300)),
        query = (dateTime, evaluationPeriod) => countEvents(REPORT_REVIEW_ON_RESPONSE, dateTime, evaluationPeriod)
      ),
      buildProbe(
        115,
        "engagementreview_probe",
        "Nombre de reviews ultérieures des consos sur la tenue des engagements",
        runInterval = 6.hours,
        evaluationPeriod = 1.day,
        expectedRange = ExpectedRange(min = Some(3)),
        query = (dateTime, evaluationPeriod) => countEvents(REPORT_REVIEW_ON_ENGAGEMENT, dateTime, evaluationPeriod)
      ),
      buildProbe(
        116,
        "reportclosednoreading_probe",
        "Nombre de signalements fermés en 'consulté ignoré'",
        runInterval = 12.hours,
        evaluationPeriod = 1.day,
        expectedRange = ExpectedRange(min = Some(10), max = Some(200)),
        query = (dateTime, evaluationPeriod) => countEvents(REPORT_CLOSED_BY_NO_ACTION, dateTime, evaluationPeriod)
      ),
      buildProbe(
        117,
        "reportclosednoaction_probe",
        "Nombre de signalements fermés en 'non consulté'",
        runInterval = 12.hours,
        evaluationPeriod = 1.day,
        expectedRange = ExpectedRange(min = Some(10), max = Some(300)),
        query = (dateTime, evaluationPeriod) => countEvents(REPORT_CLOSED_BY_NO_READING, dateTime, evaluationPeriod)
      ),
      buildProbe(
        118,
        "emailinactiveagentaccount_probe",
        "Nombre d'emails \"compte inactive\" envoyés aux agents",
        runInterval = 24.hours,
        evaluationPeriod = 7.day,
        expectedRange = ExpectedRange(min = Some(1), max = Some(300)),
        query = (dateTime, evaluationPeriod) => countEvents(EMAIL_INACTIVE_AGENT_ACCOUNT, dateTime, evaluationPeriod)
      ),
      buildProbe(
        119,
        "authattempts_successrate_probe",
        "Pourcentage de tentatives de connexion ayant réussies",
        runInterval = 30.minutes,
        evaluationPeriod = 1.hour,
        expectedRange = ExpectedRange(min = Some(10)),
        query = (dateTime, evaluationPeriod) => {
          val filter = AuthAttemptFilter(start = Some(dateTime.minusDuration(evaluationPeriod)), end = Some(dateTime))
          for {
            nbSuccesses <- authAttemptRepository.countAuthAttempts(filter.copy(isSuccess = Some(true)))
            nbTotal     <- authAttemptRepository.countAuthAttempts(filter)
          } yield computePercentage(nbSuccesses, nbTotal)
        }
      ),
      buildProbe(
        120,
        "reportfiles_pro_probe",
        "Nombre d'uploads de fichiers par des pros",
        runInterval = 6.hours,
        evaluationPeriod = 1.day,
        expectedRange = ExpectedRange(min = Some(1), max = Some(250)),
        query = (dateTime, evaluationPeriod) =>
          reportFileRepository
            .count(
              ReportFileFilter(
                start = Some(dateTime.minusDuration(evaluationPeriod)),
                end = Some(dateTime),
                origin = Some(Professional)
              )
            )
            .map(n => Some(n.toDouble))
      ),
      buildProbe(
        121,
        "reportfiles_consumer_probe",
        "Nombre d'uploads de fichiers par des consos",
        runInterval = 1.hour,
        evaluationPeriod = 2.hours,
        expectedRange = ExpectedRange(min = Some(1), max = Some(1500)),
        query = (dateTime, evaluationPeriod) =>
          reportFileRepository
            .count(
              ReportFileFilter(
                start = Some(dateTime.minusDuration(evaluationPeriod)),
                end = Some(dateTime),
                origin = Some(Consumer)
              )
            )
            .map(n => Some(n.toDouble))
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
          reportFilter.copy(start = Some(dateTime.minusDuration(evaluationPeriod)), end = Some(dateTime))
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

  private def countEvents(
      action: ActionEventValue,
      dateTime: OffsetDateTime,
      evaluationPeriod: FiniteDuration
  ): Future[Option[Double]] =
    eventRepository
      .countEvents(
        EventFilter(
          action = Some(action),
          start = Some(dateTime.minusDuration(evaluationPeriod)),
          end = Some(dateTime)
        )
      )
      .map(n => Some(n.toDouble))

  private def handleResult(
      probeName: String,
      maybeNumber: Option[Double],
      expectedRange: ExpectedRange,
      evaluationPeriod: FiniteDuration
  ) = maybeNumber match {
    case Some(p) if expectedRange.isProblematic(p) =>
      val issueAdjective = if (expectedRange.isTooHigh(p)) "trop haut" else "trop bas"
      logger.warnWithTitle("probe_triggered", s"\"$probeName\" est $issueAdjective : $p sur $evaluationPeriod")
      for {
        emailAddresses <- chooseRecipients()
        _ <- mailService
          .send(AdminProbeTriggered.Email(emailAddresses, probeName, p, issueAdjective, evaluationPeriod))
      } yield ()
    case other =>
      logger.info(s"\"$probeName\" est acceptable: $other sur $evaluationPeriod")
      Future.unit
  }

  private def chooseRecipients(): Future[Seq[EmailAddress]] = {
    val recipients = taskConfiguration.probe.recipients
    if (recipients.isEmpty) {
      for {
        users <- userRepository.listForRoles(Seq(UserRole.Admin))
      } yield users.map(_.email)
    } else Future.successful(recipients.map(EmailAddress(_)))
  }

  private def computePercentage(nb: Int, nbTotal: Int) =
    // Percentages are not significant if we don't have enough data
    if (nbTotal >= 20) {
      Some((nb.toDouble / nbTotal) * 100)
    } else {
      None
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

  implicit class OffsetDateTimeOps(dt: OffsetDateTime) {
    def minusDuration(finiteDuration: FiniteDuration) =
      dt.minusNanos(finiteDuration.toNanos)

  }

}
