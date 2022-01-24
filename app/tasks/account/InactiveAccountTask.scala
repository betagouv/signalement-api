package tasks.account

import akka.actor.ActorSystem
import cats.implicits.toTraverseOps
import config.InactiveAccountsTaskConfiguration
import play.api.Logger
import repositories.SubscriptionRepository
import repositories.UserRepository
import tasks.computeStartingTime

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class InactiveAccountTask @Inject() (
    actorSystem: ActorSystem,
    userRepository: UserRepository,
    subscriptionRepository: SubscriptionRepository,
    inactiveAccountsTaskConfiguration: InactiveAccountsTaskConfiguration
)(implicit
    executionContext: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)
  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime = inactiveAccountsTaskConfiguration.startTime
  val initialDelay: FiniteDuration = computeStartingTime(startTime)

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = 1.day) { () =>
    logger.debug(s"initialDelay - ${initialDelay}");
    runTask(LocalDate.now.atStartOfDay())
  }

  def runTask(now: LocalDateTime): Future[Unit] = {
    logger.info(s"taskDate - ${now}")

    val expirationDateThreshold: OffsetDateTime = OffsetDateTime
      .now(ZoneOffset.UTC)
      .minus(inactiveAccountsTaskConfiguration.inactivePeriod)

    logger.info(s"Removing inactive DGCCRF accounts with last validation below $expirationDateThreshold")

    for {
      inactiveDGCCRFAccounts <- userRepository.listExpiredDGCCRF(expirationDateThreshold)
      _ = logger.debug(s"Removing inactive users with ids ${inactiveDGCCRFAccounts.map(_.id)}")
      subscriptionToDelete <- inactiveDGCCRFAccounts.map(user => subscriptionRepository.list(user.id)).flatSequence
      _ = logger.debug(s"Removing subscription with ids ${subscriptionToDelete.map(_.id)}")
      _ <- inactiveDGCCRFAccounts.map(user => userRepository.delete(user.id)).sequence
      _ <- subscriptionToDelete.map(subscription => subscriptionRepository.delete(subscription.id)).sequence
      _ = logger.debug(s"Inactive DGCCRF accounts successfully removed")
    } yield inactiveDGCCRFAccounts

    Future.unit
  }

}
