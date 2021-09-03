package orchestrators

import models.ReportBlockedNotification
import repositories.ReportNotificationBlockedRepository

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportBlockedNotificationOrchestrator @Inject() (
    repository: ReportNotificationBlockedRepository
)(implicit val executionContext: ExecutionContext) {

  def findByUserId(userId: UUID): Future[Seq[ReportBlockedNotification]] = repository.findByUserId(userId)

  def createIfNotExists(userId: UUID, companyIds: Seq[UUID]): Future[Seq[ReportBlockedNotification]] =
    for {
      currentBlocked <- repository.findByUserId(userId)
      notExistingCompanyIds = companyIds.diff(currentBlocked.map(_.companyId))
      blocked <- repository.create(userId, notExistingCompanyIds)
    } yield blocked

  def delete(userId: UUID, companyIds: Seq[UUID]): Future[Int] = repository.delete(userId, companyIds)
}
