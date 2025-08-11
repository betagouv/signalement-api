package models

import enumeratum._

sealed trait UserRole extends EnumEntry

object UserRole extends PlayEnum[UserRole] {

  final case object SuperAdmin    extends UserRole
  final case object Admin         extends UserRole
  final case object ReadOnlyAdmin extends UserRole
  final case object DGCCRF        extends UserRole
  final case object DGAL          extends UserRole
  final case object SSMVM         extends UserRole
  final case object Professionnel extends UserRole

  override def values: IndexedSeq[UserRole] = findValues

  val Admins                              = List(SuperAdmin, Admin)
  val AdminsAndReadOnly                   = ReadOnlyAdmin +: Admins
  val AdminsAndReadOnlyAndCCRF            = DGCCRF +: AdminsAndReadOnly
  val AdminsAndReadOnlyAndAgents          = DGAL +: AdminsAndReadOnlyAndCCRF
  val AdminsAndReadOnlyAndAgentsWithSSMVM = SSMVM +: AdminsAndReadOnlyAndAgents
  val Agents                              = List(DGAL, DGCCRF, SSMVM)
  val AdminsAndAgents                     = Admins ++ Agents

  def isAdminOrAgent(userRole: UserRole) = AdminsAndReadOnlyAndAgentsWithSSMVM.contains(userRole)
  def isAdmin(userRole: UserRole)        = AdminsAndReadOnly.contains(userRole)
}
