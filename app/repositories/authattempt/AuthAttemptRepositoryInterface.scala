package repositories.authattempt

import models.auth.AuthAttempt
import repositories.CRUDRepositoryInterface
import utils.EmailAddress

import scala.concurrent.Future
import scala.concurrent.duration.Duration
trait AuthAttemptRepositoryInterface extends CRUDRepositoryInterface[AuthAttempt] {

  def countAuthAttempts(login: String, delay: Duration): Future[Int]

  def listAuthAttempts(login: Option[EmailAddress]): Future[Seq[AuthAttempt]]
}
