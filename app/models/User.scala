package models

import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.{EnumUtils, EmailAddress}

case class DraftUser(
  email: EmailAddress,
  firstName: String,
  lastName: String,
  password: String
)
object DraftUser {
  implicit val draftUserFormat = Json.format[DraftUser]
}

case class User (
                 id: UUID,
                 password: String,
                 email: EmailAddress,
                 firstName: String,
                 lastName: String,
                 userRole: UserRole
               ) extends Identity {
  def fullName = s"${firstName} ${lastName}"
}

object User {
  implicit val userWrites = new Writes[User] {
    def writes(user: User) = Json.obj(
      "id" -> user.id,
      "email" -> user.email,
      "firstName" -> user.firstName,
      "lastName" -> user.lastName,
      "role" -> user.userRole.name,
      "permissions" -> user.userRole.permissions
    )
  }

  implicit val userReads: Reads[User] = (
    (JsPath \ "id").read[UUID] and
      (JsPath \ "password").read[String] and
      (JsPath \ "email").read[EmailAddress] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String] and
      ((JsPath \ "role").read[String]).map(UserRoles.withName(_))
    )(User.apply _)
}

case class UserLogin(
                      login: String,
                      password: String
                    )

case class AuthAttempt(
  id: UUID,
  login: String,
  timestamp: OffsetDateTime
)

object UserLogin {
  implicit val userLoginFormat = Json.format[UserLogin]
}

object UserPermission extends Enumeration {
  val listReports,
      updateReport,
      deleteReport,
      deleteFile,
      createReportAction,
      activateAccount,
      editDocuments,
      subscribeReports,
      inviteDGCCRF = Value

  implicit val enumReads: Reads[UserPermission.Value] = EnumUtils.enumReads(UserPermission)

  implicit def enumWrites: Writes[UserPermission.Value] = EnumUtils.enumWrites
}

case class UserRole (
                    name: String,
                    permissions: Seq[UserPermission.Value]
                    ) {
}


object UserRole {
  implicit val userRoleWrites = Json.writes[UserRole]

  implicit val userRoleReads: Reads[UserRole] =  ((JsPath \ "role").read[String]).map(UserRoles.withName(_))
}

object UserRoles {

  object Admin extends UserRole(
    "Admin",
    UserPermission.values.toSeq
  )

  object DGCCRF extends UserRole(
    "DGCCRF",
    Seq(
      UserPermission.listReports,
      UserPermission.createReportAction,
      UserPermission.subscribeReports
    )
  )

  object Pro extends UserRole(
    "Professionnel",
    Seq(
      UserPermission.listReports,
      UserPermission.createReportAction
    )
  )

  val userRoles = Seq(Admin, DGCCRF, Pro)

  def withName(name: String): UserRole = {
    userRoles.filter(_.name == name).head
  }
}

case class PasswordChange(
                           newPassword: String,
                           oldPassword: String
                         )

object PasswordChange {
  implicit val userReads: Reads[PasswordChange] = (
    (JsPath \ "newPassword").read[String] and
      (JsPath \ "oldPassword").read[String]
    )(PasswordChange.apply _).filter(JsonValidationError("Passwords must not be equals"))(passwordChange => passwordChange.newPassword != passwordChange.oldPassword)
}

case class AuthToken(
                      id: UUID,
                      userID: UUID,
                      expiry: OffsetDateTime
                    )
