package models

import java.util.UUID

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.EnumUtils

case class User (
                 id: Option[UUID],
                 email: String,
                 password: String,
                 firstName: String,
                 lastName: String,
                 userRole: UserRole
               ) extends Identity


case class UserLogin(
                      email: String,
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
      createEvent = Value

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

object User {
  implicit val userWrites = new Writes[User] {
    def writes(user: User) = Json.obj(
      "id" -> user.id,
      "email" -> user.email,
      "firstName" -> user.firstName,
      "lastName" -> user.lastName,
      "role" -> user.userRole.name
    )
  }

  implicit val userReads: Reads[User] = (
    (JsPath \ "id").readNullable[UUID] and
      (JsPath \ "email").read[String] and
      (JsPath \ "password").read[String] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String] and
      ((JsPath \ "role").read[String]).map(UserRoles.withName(_))
    )(User.apply _)
}

object UserRoles {

  object Admin extends UserRole(
    "Admin",
    UserPermission.values.toSeq
  )

  val userRoles = Seq(Admin)

  def withName(name: String): UserRole = {
    userRoles.filter(_.name == name).head
  }
}
