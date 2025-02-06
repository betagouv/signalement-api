package models

import enumeratum._

sealed trait UserRole extends EnumEntry

object UserRole extends PlayEnum[UserRole] {

  final case object SuperAdmin    extends UserRole
  final case object Admin         extends UserRole
  final case object ReadOnlyAdmin extends UserRole
  final case object DGCCRF        extends UserRole
  final case object DGAL          extends UserRole
  final case object Professionnel extends UserRole

  override def values: IndexedSeq[UserRole] = findValues

  val Admins                     = List(SuperAdmin, Admin)
  val AdminsAndReadOnly          = ReadOnlyAdmin +: Admins
  val AdminsAndReadOnlyAndCCRF   = DGCCRF +: AdminsAndReadOnly
  val AdminsAndReadOnlyAndAgents = DGAL +: AdminsAndReadOnlyAndCCRF
  val Agents                     = List(DGAL, DGCCRF)
  val AdminsAndAgents            = Admins ++ Agents

  def isAdminOrAgent(userRole: UserRole) = AdminsAndReadOnlyAndAgents.contains(userRole)
  def isAdmin(userRole: UserRole)        = AdminsAndReadOnly.contains(userRole)
}
