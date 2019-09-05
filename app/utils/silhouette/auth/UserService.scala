package utils.silhouette.auth

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import javax.inject.Inject
import models.User
import repositories.UserRepository
import Implicits._

import scala.concurrent.{ExecutionContext, Future}

//Utilise par Silhouette pour recuperer l'identite d'un user au travers du token JWT de la request
class UserService @Inject() (userRepository: UserRepository)
                            (implicit val executionContext: ExecutionContext)
  extends IdentityService[User] {

  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = userRepository.findByLogin(loginInfo)

  def save(user: User): Future[User] = userRepository.update(user).map( _ => user)
}