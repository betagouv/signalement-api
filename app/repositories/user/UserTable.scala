package repositories.user

import models.User
import models.UserRole
import repositories.DatabaseTable
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID
import repositories.PostgresProfile.api._

class UserTable(tag: Tag) extends DatabaseTable[User](tag, "users") {

  def password            = column[String]("password")
  def email               = column[EmailAddress]("email")
  def firstName           = column[String]("firstname")
  def lastName            = column[String]("lastname")
  def role                = column[String]("role")
  def lastEmailValidation = column[Option[OffsetDateTime]]("last_email_validation")
  def deletionDate        = column[Option[OffsetDateTime]]("deletion_date")

  type UserData =
    (
        UUID,
        String,
        EmailAddress,
        String,
        String,
        String,
        Option[OffsetDateTime],
        Option[OffsetDateTime]
    )

  def constructUser: UserData => User = {
    case (
          id,
          password,
          email,
          firstName,
          lastName,
          role,
          lastEmailValidation,
          deletionDate
        ) =>
      User(
        id,
        password,
        email,
        firstName,
        lastName,
        UserRole.withName(role),
        lastEmailValidation,
        deletionDate = deletionDate,
        impersonator = None
      )
  }

  def extractUser: PartialFunction[User, UserData] = {
    case User(
          id,
          password,
          email,
          firstName,
          lastName,
          role,
          lastEmailValidation,
          deletionDate,
          _
        ) =>
      (
        id,
        password,
        email,
        firstName,
        lastName,
        role.entryName,
        lastEmailValidation,
        deletionDate
      )
  }

  def * = (
    id,
    password,
    email,
    firstName,
    lastName,
    role,
    lastEmailValidation,
    deletionDate
  ) <> (constructUser, extractUser.lift)
}

object UserTable {
  val fullTableIncludingDeleted = TableQuery[UserTable]
  // 99% of the queries should exclude the deleted users
  val table: Query[UserTable, UserTable#TableElementType, Seq] =
    fullTableIncludingDeleted.filter(_.deletionDate.isEmpty)
}
