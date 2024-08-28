package models

import enumeratum._

sealed trait UserRole extends EnumEntry {
  val permissions: Seq[UserPermission.Value]
  final def hasPermission(permission: UserPermission.Value) = permissions.contains(permission)

  val isAgentOrAdmin: Boolean
}

object UserRole extends PlayEnum[UserRole] {

  final case object Admin extends UserRole {
    override val permissions    = UserPermission.values.toSeq
    override val isAgentOrAdmin = true
  }

  final case object DGCCRF extends UserRole {
    override val permissions = Seq(
      UserPermission.listReports,
      UserPermission.createReportAction,
      UserPermission.subscribeReports,
      UserPermission.viewConsumerReviewDetails
    )
    override val isAgentOrAdmin = true
  }

  final case object DGAL extends UserRole {
    override val permissions = Seq(
      UserPermission.listReports,
      UserPermission.createReportAction,
      UserPermission.subscribeReports,
      UserPermission.viewConsumerReviewDetails
    )
    override val isAgentOrAdmin = true
  }

  final case object Professionnel extends UserRole {
    override val permissions = Seq(
      UserPermission.listReports,
      UserPermission.createReportAction
    )
    override val isAgentOrAdmin = false

  }

  override def values: IndexedSeq[UserRole] = findValues
}
