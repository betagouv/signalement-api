package models

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.EmailAddress
import utils.EnumUtils

import java.time.OffsetDateTime
import java.util.UUID

case class DraftUser(
    email: EmailAddress,
    firstName: String,
    lastName: String,
    password: String
)

object DraftUser {
  implicit val draftUserFormat = Json.format[DraftUser]
}

case class User(
    id: UUID,
    password: String,
    email: EmailAddress,
    firstName: String,
    lastName: String,
    userRole: UserRole,
    lastEmailValidation: Option[OffsetDateTime]
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
      "role" -> user.userRole.entryName,
      "permissions" -> user.userRole.permissions,
      "lastEmailValidation" -> user.lastEmailValidation
    )
  }

  implicit val userReads: Reads[User] = (
    (JsPath \ "id").read[UUID] and
      (JsPath \ "password").read[String] and
      (JsPath \ "email").read[EmailAddress] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String] and
      ((JsPath \ "role").read[String]).map(UserRole.withName) and
      (JsPath \ "lastEmailValidation").readNullable[OffsetDateTime]
  )(User.apply _)
}

case class UserLogin(
    login: String,
    password: String
)

case class AuthAttempt(
    id: UUID,
    login: String,
    timestamp: OffsetDateTime,
    isSuccess: Option[Boolean],
    failureCause: Option[String] = None
)

object UserLogin {
  implicit val userLoginFormat = Json.format[UserLogin]
}

object UserPermission extends Enumeration {
  val listReports, updateReport, deleteReport, deleteFile, createReportAction, activateAccount, updateCompany,
      editDocuments, subscribeReports, inviteDGCCRF = Value

  implicit val enumReads: Reads[UserPermission.Value] = EnumUtils.enumReads(UserPermission)

  implicit def enumWrites: Writes[UserPermission.Value] = EnumUtils.enumWrites
}

case class PasswordChange(
    newPassword: String,
    oldPassword: String
)

object PasswordChange {
  implicit val userReads: Reads[PasswordChange] = (
    (JsPath \ "newPassword").read[String] and
      (JsPath \ "oldPassword").read[String]
  )(PasswordChange.apply _).filter(JsonValidationError("Passwords must not be equals"))(passwordChange =>
    passwordChange.newPassword != passwordChange.oldPassword
  )
}

case class AuthToken(
    id: UUID,
    userID: UUID,
    expiry: OffsetDateTime
)
