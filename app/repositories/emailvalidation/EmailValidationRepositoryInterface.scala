package repositories.emailvalidation

import models.EmailValidation
import repositories.CRUDRepositoryInterface
import utils.EmailAddress

import scala.concurrent.Future

trait EmailValidationRepositoryInterface extends CRUDRepositoryInterface[EmailValidation] {
  def findByEmail(email: EmailAddress): Future[Option[EmailValidation]]

  def validate(email: EmailAddress): Future[Option[EmailValidation]]

  def update(email: EmailValidation): Future[Int]

  def exists(email: EmailAddress): Future[Boolean]

  def isValidated(email: EmailAddress): Future[Boolean]
}
