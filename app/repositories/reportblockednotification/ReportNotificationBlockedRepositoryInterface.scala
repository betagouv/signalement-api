package repositories.reportblockednotification
import com.google.inject.ImplementedBy
import models.report.ReportBlockedNotification
import utils.EmailAddress

import java.util.UUID
import scala.concurrent.Future

@ImplementedBy(classOf[ReportNotificationBlockedRepository])
trait ReportNotificationBlockedRepositoryInterface {

  def findByUserId(userId: UUID): Future[Seq[ReportBlockedNotification]]

  def filterBlockedEmails(email: Seq[EmailAddress], companyId: UUID): Future[Seq[EmailAddress]]

  def create(userId: UUID, companyIds: Seq[UUID]): Future[Seq[ReportBlockedNotification]]

  def delete(userId: UUID, companyIds: Seq[UUID]): Future[Int]
}
