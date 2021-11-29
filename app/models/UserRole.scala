package models

import enumeratum._
//import play.api.libs.json.JsPath
//import play.api.libs.json.Json
//import play.api.libs.json.Reads

sealed trait UserRole extends EnumEntry {
  val permissions: Seq[UserPermission.Value]
}

object UserRole extends Enum[UserRole] {

  final case object Admin extends UserRole {
    override val permissions = UserPermission.values.toSeq
  }

  final case object DGCCRF extends UserRole {
    override val permissions = Seq(
      UserPermission.listReports,
      UserPermission.createReportAction,
      UserPermission.subscribeReports
    )
  }

  final case object Professionnel extends UserRole {
    override val permissions = Seq(
      UserPermission.listReports,
      UserPermission.createReportAction
    )
  }

  override def values: IndexedSeq[UserRole] = findValues
//  implicit val userRoleWrites = Json.writes[UserRole]
//
//  implicit val userRoleReads: Reads[UserRole] = ((JsPath \ "role").read[String]).map(UserRole.withName(_))
}
