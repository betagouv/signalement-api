package repositories.emailvalidation

import models.EmailValidation
import models.EmailValidationCreate
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class EmailValidationRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  def find(id: UUID): Future[Option[EmailValidation]] =
    db.run(EmailValidationTable.table.filter(_.id === id).result.headOption)

  def findByEmail(email: EmailAddress): Future[Option[EmailValidation]] =
    db.run(EmailValidationTable.table.filter(_.email === email).result.headOption)

  def validate(email: EmailAddress): Future[Option[EmailValidation]] = {
    val action = (for {
      _ <- EmailValidationTable.table
        .filter(_.email === email)
        .map(_.lastValidationDate)
        .update(Some(OffsetDateTime.now()))
      updated <- EmailValidationTable.table.filter(_.email === email).result.headOption
    } yield updated).transactionally
    db.run(action)
  }

  def update(email: EmailValidation): Future[Int] =
    db.run(EmailValidationTable.table.filter(_.email === email.email).update(email))

  def exists(email: EmailAddress): Future[Boolean] =
    db.run(EmailValidationTable.table.filter(_.email === email).result.headOption).map(_.isDefined)

  def create(newEmailValidation: EmailValidationCreate): Future[EmailValidation] = {
    val entity = newEmailValidation.toEntity
    db.run(EmailValidationTable.table += entity).map(_ => entity)
  }

  def list(): Future[Seq[EmailValidation]] =
    db.run(EmailValidationTable.table.result)

  def isValidated(email: EmailAddress): Future[Boolean] =
    db.run(
      EmailValidationTable.table
        .filter(_.email === email)
        .filter(_.lastValidationDate.isDefined)
        .result
        .headOption
    ).map(_.isDefined)

  def delete(id: UUID): Future[Int] = db.run(EmailValidationTable.table.filter(_.id === id).delete)
}
