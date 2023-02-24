package utils.silhouette.auth

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import play.api.Logger
import repositories.user.UserRepositoryInterface
import utils.silhouette.Credentials.toPasswordInfo

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.ClassTag

// This class is only used to authenticate a deleted DGCCRF user to send him a message that his account has been deleted
// We don't want to perform any other action on deleted users
class PasswordInfoIncludingDeletedDAO(userRepository: UserRepositoryInterface)(implicit
    val classTag: ClassTag[PasswordInfo],
    ec: ExecutionContext
) extends DelegableAuthInfoDAO[PasswordInfo] {

  val logger: Logger = Logger(this.getClass)

  def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    throw new Error("Unexpected call to PasswordInfoDAO.add()")

  def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] =
    userRepository.findByEmailIncludingDeleted(loginInfo.providerKey).map {
      case Some(user) =>
        Some(toPasswordInfo(user.password))
      case _ => None
    }

  def remove(loginInfo: LoginInfo): Future[Unit] =
    throw new Error("Unexpected call to PasswordInfoDAO.remove()")

  def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    throw new Error("Unexpected call to PasswordInfoDAO.save()")

  def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    throw new Error("Unexpected call to PasswordInfoDAO.update()")

}
