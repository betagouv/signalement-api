package orchestrators

import models.UserRole
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
      rateIsProblematicCriterion: Double => Boolean,
      probeName: String,
      issueAdjective: String
  )(implicit
      executionContext: ExecutionContext
  ) = maybeRate match {
    case Some(r) if rateIsProblematicCriterion(r) =>
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
