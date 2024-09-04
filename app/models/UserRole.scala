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
  val EveryoneButReadOnlyAdmin   = List(SuperAdmin, Admin, DGCCRF, DGAL, Professionnel)

  def isAdminOrAgent(userRole: UserRole) = AdminsAndReadOnlyAndAgents.contains(userRole)
}
