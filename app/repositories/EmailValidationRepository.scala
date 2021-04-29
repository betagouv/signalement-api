package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.{EmailValidation, EmailValidationCreate}
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailValidationRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(
  implicit ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  class EmailValidationTable(tag: Tag) extends Table[EmailValidation](tag, "emails_validation") {
    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def email = column[EmailAddress]("email")
    def lastValidationDate = column[Option[OffsetDateTime]]("last_validation_date")
    def * = (id, creationDate, email, lastValidationDate) <> ((EmailValidation.apply _).tupled, EmailValidation.unapply)
  }

  val emailTableQuery = TableQuery[EmailValidationTable]

  def find(id: UUID): Future[Option[EmailValidation]] = {
    db.run(emailTableQuery.filter(_.id === id).result.headOption)
  }

  def findByEmail(email: EmailAddress): Future[Option[EmailValidation]] = {
    db.run(emailTableQuery.filter(_.email === email).result.headOption)
  }

  def validate(email: EmailAddress): Future[Option[EmailValidation]] = {
    val action = (for {
      _ <- emailTableQuery.filter(_.email === email).map(_.lastValidationDate).update(Some(OffsetDateTime.now()))
      updated <- emailTableQuery.filter(_.email === email).result.headOption
    } yield updated).transactionally
    db.run(action)
  }

  def exists(email: EmailAddress): Future[Boolean] = {
    db.run(emailTableQuery.filter(_.email === email).result.headOption).map(_.isDefined)
  }

  def create(newEmailValidation: EmailValidationCreate): Future[EmailValidation] = {
    val entity = newEmailValidation.toEntity()
    db.run(emailTableQuery += entity).map(_ => entity)
  }

  def list(): Future[Seq[EmailValidation]] = {
    db.run(emailTableQuery.result)
  }

  def isValidated(email: EmailAddress): Future[Boolean] = {
    db.run(emailTableQuery
      .filter(_.email === email)
      .filter(_.lastValidationDate.isDefined)
      .result.headOption
    ).map(_.isDefined)
  }

  def delete(id: UUID): Future[Int] = db.run(emailTableQuery.filter(_.id === id).delete)
}
