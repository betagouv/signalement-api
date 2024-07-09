package repositories.emailvalidation

import models.EmailValidation
import models.EmailValidationFilter
import models.PaginatedResult
import models.PaginatedSearch
import repositories.CRUDRepositoryInterface
import utils.EmailAddress

import java.time.OffsetDateTime
import scala.concurrent.Future

trait EmailValidationRepositoryInterface extends CRUDRepositoryInterface[EmailValidation] {
  def findByEmail(email: EmailAddress): Future[Option[EmailValidation]]

  def findSimilarEmail(email: EmailAddress, createdAfter: OffsetDateTime): Future[Option[EmailValidation]]

  def validate(email: EmailAddress): Future[Option[EmailValidation]]

  def update(email: EmailValidation): Future[Int]

  def exists(email: EmailAddress): Future[Boolean]

  def search(filter: EmailValidationFilter, paginate: PaginatedSearch): Future[PaginatedResult[EmailValidation]]

  def count(filter: EmailValidationFilter): Future[Int]
}
