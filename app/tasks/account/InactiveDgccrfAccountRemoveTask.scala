package tasks.account

import cats.implicits.toTraverseOps
import models.User
import play.api.Logger
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.subscription.SubscriptionRepositoryInterface
import repositories.user.UserRepositoryInterface
import tasks.model.TaskType
import tasks.TaskExecutionResult
import tasks.TaskExecutionResults
import tasks.toValidated

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import utils.Logs.RichLogger

class InactiveDgccrfAccountRemoveTask(
    userRepository: UserRepositoryInterface,
    subscriptionRepository: SubscriptionRepositoryInterface,
    asyncFileRepository: AsyncFileRepositoryInterface
)(implicit executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  def removeInactiveAccounts(expirationDateThreshold: OffsetDateTime): Future[TaskExecutionResults] = {

    logger.info(s"Soft delete inactive DGCCRF accounts with last validation below $expirationDateThreshold")
    for {
      inactiveDGCCRFAccounts <- userRepository.listExpiredDGCCRF(expirationDateThreshold)
      results                <- inactiveDGCCRFAccounts.map(removeWithSubscriptions).sequence
    } yield results.sequence
  }

  private def removeWithSubscriptions(user: User): Future[TaskExecutionResult] = {
    // We delete some non-important stuff (subscriptions/files)
    // We keep the events, it seems too much important for debugging, stats, etc.
    val executionOrError = for {
      subscriptionToDelete <- subscriptionRepository.list(user.id)
      _ = logger.debug(s"Deleting subscription with ids ${subscriptionToDelete.map(_.id)}")
      _ <- subscriptionToDelete.map(subscription => subscriptionRepository.delete(subscription.id)).sequence
      _ = logger.debug(s"Deleting files of user ${user.id}")
      _ <- asyncFileRepository.deleteByUserId(user.id)
      _ = logger.infoWithTitle("inactive_dgccrf_account_task_item", s"Soft deleting user ${user.id}")
      _ <- userRepository.softDelete(user.id)
      _ = logger.debug(s"Inactive DGCCRF account user ${user.id} successfully deleted")
    } yield ()

    toValidated(executionOrError, user.id, TaskType.InactiveAccountClean)

  }

}
