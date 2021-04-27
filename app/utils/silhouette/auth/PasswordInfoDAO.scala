package utils.silhouette.auth

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import javax.inject.Inject
import play.api.Logger
import repositories.UserRepository
import utils.silhouette.SilhouetteUtils
import utils.EmailAddress

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class PasswordInfoDAO @Inject() (userRepository: UserRepository)(implicit val classTag: ClassTag[PasswordInfo])
      extends DelegableAuthInfoDAO[PasswordInfo] {

  val logger: Logger = Logger(this.getClass())

  def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    update(loginInfo, authInfo)

  def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] = {
    userRepository.findByLogin(SilhouetteUtils.loginInfo2key(loginInfo)).map {
      case Some(user) => Some(SilhouetteUtils.pwd2passwordInfo(user.password))
      case _ => None
    }
  }

  def remove(loginInfo: LoginInfo): Future[Unit] = userRepository.delete(EmailAddress(SilhouetteUtils.loginInfo2key(loginInfo))).map(_ => ())    // FIXME: Is it used ?

  def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    find(loginInfo).flatMap {
      case Some(_) => update(loginInfo, authInfo)
      case None => add(loginInfo, authInfo)
    }

  def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] =
    userRepository.findByLogin(SilhouetteUtils.loginInfo2key(loginInfo)).map {
      case Some(user) => {
        userRepository.update(user.copy(password = SilhouetteUtils.passwordInfo2pwd(authInfo)))
        authInfo
      }
      case _ => throw new Exception("PasswordInfoDAO - update : the user must exists to update its password")
    }

}