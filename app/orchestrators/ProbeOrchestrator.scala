package orchestrators

import models.UserRole
import orchestrators.ProbeOrchestrator.ExpectedRange
import play.api.Logger
import repositories.user.UserRepositoryInterface
import services.emails.EmailDefinitionsAdmin.AdminProbeTriggered
import services.emails.MailServiceInterface
import utils.Logs.RichLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ProbeOrchestrator(
    userRepository: UserRepositoryInterface,
    mailService: MailServiceInterface
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(getClass)

  def handleProbeResult(
      probeName: String,
      maybePercentage: Option[Double],
      expectedRange: ExpectedRange,
      evaluationPeriod: FiniteDuration
  ) = maybePercentage match {
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

}
