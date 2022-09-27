package repositories.user

import models.User
import models.UserRole
import repositories.CRUDRepositoryInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

trait UserRepositoryInterface extends CRUDRepositoryInterface[User] {

  def listExpiredDGCCRF(expirationDate: OffsetDateTime): Future[List[User]]

  def listIncludingDeleted(roles: Seq[UserRole]): Future[Seq[User]]

  def create(user: User): Future[User]

  def updatePassword(userId: UUID, password: String): Future[Int]

  def findByEmail(email: String): Future[Option[User]]

  def softDelete(id: UUID): Future[Int]
}
