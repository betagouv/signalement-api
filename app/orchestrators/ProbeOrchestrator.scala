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

class ProbeOrchestrator(
    userRepository: UserRepositoryInterface,
    mailService: MailServiceInterface
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(getClass)

  def handleProbeResult(
      maybeRate: Option[Double],
      expectedRange: ExpectedRange,
      probeName: String
  ) = maybeRate match {
    case Some(r) if expectedRange.isProblematic(r) =>
      val issueAdjective = if (expectedRange.isTooHigh(r)) "trop haut" else "trop bas"
      logger.warnWithTitle("probe_triggered", s"$probeName est $issueAdjective : $r%")
      for {
        users <- userRepository.listForRoles(Seq(UserRole.Admin))
        _ <- mailService
          .send(
            AdminProbeTriggered
              .Email(users.map(_.email), probeName, r, issueAdjective)
          )
      } yield ()
    case rate =>
      logger.info(s"$probeName est correct: $rate%")
      Future.unit
  }

}

object ProbeOrchestrator {

  case class ExpectedRange(
      min: Option[Double],
      max: Option[Double]
  ) {
    def isProblematic(rate: Double): Boolean =
      isTooHigh(rate) || isTooLow(rate)
    def isTooHigh(rate: Double): Boolean =
      max.exists(rate > _)
    private def isTooLow(rate: Double): Boolean =
      min.exists(rate < _)
  }

}
