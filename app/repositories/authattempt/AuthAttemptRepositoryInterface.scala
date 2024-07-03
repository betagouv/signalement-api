package repositories.authattempt

import models.auth.AuthAttempt
import models.auth.AuthAttemptFilter
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future
import scala.concurrent.duration.Duration
trait AuthAttemptRepositoryInterface extends CRUDRepositoryInterface[AuthAttempt] {

  def countAuthAttempts(login: String, delay: Duration): Future[Int]

  def countAuthAttempts(filter: AuthAttemptFilter): Future[Int]

  def listAuthAttempts(login: Option[String]): Future[Seq[AuthAttempt]]
}
