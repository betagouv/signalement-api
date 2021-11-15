package models

import enumeratum._
import models.UserRoles.Admin
import models.UserRoles.DGCCRF

sealed trait ReportStatus extends EnumEntry

object ReportStatus extends PlayEnum[ReportStatus] {

  val values = findValues

  case object NA extends ReportStatus
  case object LanceurAlerte extends ReportStatus
  case object TraitementEnCours extends ReportStatus
  case object Transmis extends ReportStatus
  case object PromesseAction extends ReportStatus
  case object Infonde extends ReportStatus
  case object NonConsulte extends ReportStatus
  case object ConsulteIgnore extends ReportStatus
  case object MalAttribue extends ReportStatus

  val statusVisibleByPro: Seq[ReportStatus] =
    Seq(
      TraitementEnCours,
      Transmis,
      PromesseAction,
      Infonde,
      NonConsulte,
      ConsulteIgnore,
      MalAttribue
    )

  val ReportStatusProResponse = Seq(ReportStatus.PromesseAction, ReportStatus.Infonde, ReportStatus.MalAttribue)

  def filterByUserRole(status: Seq[ReportStatus], userRole: Option[UserRole]) = {
    val requestedStatus = if (status.isEmpty) ReportStatus.values else status
    userRole match {
      case Some(Admin)  => requestedStatus
      case Some(DGCCRF) => requestedStatus
      case _            => requestedStatus.intersect(statusVisibleByPro)
    }
  }

  def isFinal(status: ReportStatus): Boolean =
    Seq(MalAttribue, ConsulteIgnore, NonConsulte, Infonde, PromesseAction, LanceurAlerte, NA).contains(status)

  def translate(status: ReportStatus, userRole: UserRole): String = {
    def isPro = userRole == UserRoles.Pro
    status match {
      case NA                => if (isPro) "" else "NA"
      case LanceurAlerte     => if (isPro) "" else "Lanceur d'alerte"
      case TraitementEnCours => if (isPro) "Non consulté" else "Traitement en cours"
      case Transmis          => if (isPro) "À répondre" else "Signalement transmis"
      case PromesseAction    => if (isPro) "Clôturé" else "Promesse action"
      case Infonde           => if (isPro) "Clôturé" else "Signalement infondé"
      case NonConsulte       => if (isPro) "Clôturé" else "Signalement non consulté"
      case ConsulteIgnore    => if (isPro) "Clôturé" else "Signalement consulté ignoré"
      case MalAttribue       => if (isPro) "Clôturé" else "Signalement mal attribué"
    }
  }
}
