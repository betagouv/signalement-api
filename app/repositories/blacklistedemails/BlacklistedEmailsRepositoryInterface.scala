package repositories.blacklistedemails

import models.BlacklistedEmail
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

trait BlacklistedEmailsRepositoryInterface extends CRUDRepositoryInterface[BlacklistedEmail] {
  def isBlacklisted(email: String): Future[Boolean]
}
