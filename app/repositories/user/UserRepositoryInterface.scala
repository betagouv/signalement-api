package repositories.user

import models.User
import models.UserRole
import repositories.CRUDRepositoryInterface
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

trait UserRepositoryInterface extends CRUDRepositoryInterface[User] {

  def listExpiredDGCCRF(expirationDate: OffsetDateTime): Future[List[User]]

  def listInactiveDGCCRFWithSentEmailCount(
      reminderDate: OffsetDateTime,
      expirationDate: OffsetDateTime
  ): Future[List[(User, Option[Int])]]

  def listForRoles(roles: Seq[UserRole]): Future[Seq[User]]

  def listDeleted(): Future[Seq[User]]

  def create(user: User): Future[User]

  def updatePassword(userId: UUID, password: String): Future[Int]

  def findByEmail(email: String): Future[Option[User]]

  def findByEmailIncludingDeleted(email: String): Future[Option[User]]

  def softDelete(id: UUID): Future[Int]

  def findByEmails(emails: List[EmailAddress]): Future[Seq[User]]
}
