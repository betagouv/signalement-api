package repositories.authattempt

import com.google.inject.ImplementedBy
import models.auth.AuthAttempt
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future
import scala.concurrent.duration.Duration
@ImplementedBy(classOf[AuthAttemptRepository])
trait AuthAttemptRepositoryInterface extends CRUDRepositoryInterface[AuthAttempt] {

  def countAuthAttempts(login: String, delay: Duration): Future[Int]

  def listAuthAttempts(login: String): Future[Seq[AuthAttempt]]
}
