package repositories.emailvalidation

import models.EmailValidation
import models.EmailValidationFilter
import models.PaginatedResult
import models.PaginatedSearch
import play.api.Logger
import repositories.CRUDRepository
import repositories.PostgresProfile
import slick.jdbc.JdbcProfile
import utils.EmailAddress
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import PostgresProfile.api._
import slick.basic.DatabaseConfig

class EmailValidationRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[EmailValidationTable, EmailValidation]
    with EmailValidationRepositoryInterface {

  val SplitPartEmail     = SimpleFunction.ternary[EmailAddress, String, Int, String]("split_part")
  val SplitPartString    = SimpleFunction.ternary[String, String, Int, String]("split_part")
  val ReplaceSQLFunction = SimpleFunction.ternary[String, String, String, String]("replace")

  val logger: Logger                                   = Logger(this.getClass)
  override val table: TableQuery[EmailValidationTable] = EmailValidationTable.table
  import dbConfig._

  override def findByEmail(email: EmailAddress): Future[Option[EmailValidation]] =
    db.run(table.filter(_.email === email).result.headOption)

  def findSimilarEmail(email: EmailAddress, createdAfter: OffsetDateTime): Future[Option[EmailValidation]] = {

    val rootGmailAddress = s"${email.split.rootAddress}@gmail.com"

    db.run(
      table
        .filter(_.creationDate >= createdAfter)
        .filter(emailValidation =>
          Case If SplitPartEmail(emailValidation.email, "@", 2) === "gmail.com"
            Then ReplaceSQLFunction(
              SplitPartString(SplitPartEmail(emailValidation.email, "@", 1), "+", 1),
              ".",
              ""
            ) ++ "@gmail.com" === rootGmailAddress
            Else emailValidation.email === email
        )
        .result
        .headOption
    )
  }

  override def validate(email: EmailAddress): Future[Option[EmailValidation]] = {
    val action = (for {
      _ <- table
        .filter(_.email === email)
        .map(_.lastValidationDate)
        .update(Some(OffsetDateTime.now()))
      updated <- table.filter(_.email === email).result.headOption
    } yield updated).transactionally
    db.run(action)
  }

  override def update(email: EmailValidation): Future[Int] =
    db.run(table.filter(_.email === email.email).update(email))

  override def exists(email: EmailAddress): Future[Boolean] =
    db.run(table.filter(_.email === email).result.headOption).map(_.isDefined)

  def search(filter: EmailValidationFilter, paginate: PaginatedSearch): Future[PaginatedResult[EmailValidation]] =
    queryFilter(filter)
      .sortBy(_.creationDate.desc)
      .withPagination(db)(paginate.offset, paginate.limit)
  override def count(filter: EmailValidationFilter) =
    db.run(queryFilter(filter).length.result)
  private def queryFilter(filter: EmailValidationFilter) =
    table
      .filterOpt(filter.start) { case (table, start) =>
        table.creationDate >= start
      }
      .filterOpt(filter.end) { case (table, end) =>
        table.creationDate <= end
      }
      .filterOpt(filter.email)(_.email === _)
      .filterOpt(filter.validated) { (emailValidationTable, searchValidated) =>
        val expiredValidations = emailValidationTable.lastValidationDate
          .filter(_ < EmailValidation.EmailValidationThreshold)
        if (searchValidated) {
          emailValidationTable.lastValidationDate.isDefined && expiredValidations.isEmpty
        } else {
          emailValidationTable.lastValidationDate.isEmpty || expiredValidations.nonEmpty
        }
      }
      .sortBy(_.creationDate.desc)

}
