package models

import java.time.OffsetDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.EnumUtils
import utils.EmailAddress

case class DraftUser(
  email: String,
  firstName: String,
  lastName: String,
  password: String
)
object DraftUser {
  implicit val draftUserFormat = Json.format[DraftUser]
}

case class User (
                 id: UUID,
                 login: String,
                 password: String,
                 activationKey: Option[String],
                 email: Option[EmailAddress],
                 firstName: Option[String],
                 lastName: Option[String],
                 userRole: UserRole
               ) extends Identity

object User {
  implicit val userWrites = new Writes[User] {
    def writes(user: User) = Json.obj(
      "id" -> user.id,
      "login" -> user.login,
      "email" -> user.email,
      "firstName" -> user.firstName,
      "lastName" -> user.lastName,
      "role" -> user.userRole.name,
      "permissions" -> user.userRole.permissions
    )
  }

  implicit val userReads: Reads[User] = (
    (JsPath \ "id").read[UUID] and
      (JsPath \ "login").read[String] and
      (JsPath \ "password").read[String] and
      (JsPath \ "activationKey").readNullable[String] and
      (JsPath \ "email").readNullable[EmailAddress] and
      (JsPath \ "firstName").readNullable[String] and
      (JsPath \ "lastName").readNullable[String] and
      ((JsPath \ "role").read[String]).map(UserRoles.withName(_))
    )(User.apply _)
}

case class UserLogin(
                      login: String,
                      password: String
                    )

object UserLogin {
  implicit val userLoginFormat = Json.format[UserLogin]
}

object UserPermission extends Enumeration {
  val listReports,
      updateReport,
      deleteReport,
      deleteFile,
      createEvent,
      activateAccount,
      editDocuments,
      subscribeReports = Value

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
      UserPermission.createEvent,
      UserPermission.subscribeReports
    )
  )

  object ToActivate extends UserRole(
    "ToActivate",
    Seq(UserPermission.activateAccount)
  )

  object Pro extends UserRole(
    "Professionnel",
    Seq(
      UserPermission.listReports,
      UserPermission.createEvent
    )
  )

  val userRoles = Seq(Admin, DGCCRF, Pro, ToActivate)

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
