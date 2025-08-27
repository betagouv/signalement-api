package models.report

import enumeratum._
import models.UserRole

sealed trait ReportStatus extends EnumEntry

object ReportStatus extends PlayEnum[ReportStatus] {

  val values = findValues

  case object NA                 extends ReportStatus
  case object InformateurInterne extends ReportStatus

  /** Not read by pro status
    */
  case object TraitementEnCours extends ReportStatus
  case object NonConsulte       extends ReportStatus

  /** Read by pro status
    */
  case object Transmis        extends ReportStatus
  case object PromesseAction  extends ReportStatus
  case object Infonde         extends ReportStatus
  case object ConsulteIgnore  extends ReportStatus
  case object MalAttribue     extends ReportStatus
  case object SuppressionRGPD extends ReportStatus

  val statusAvailableForConsultationByPro =
    Seq(
      TraitementEnCours,
      Transmis,
      PromesseAction,
      Infonde,
      NonConsulte,
      ConsulteIgnore,
      MalAttribue
    )

  val statusVisibleByPro: Seq[ReportStatus] =
    statusAvailableForConsultationByPro :+ SuppressionRGPD

  val statusWithProResponse = Seq(PromesseAction, Infonde, MalAttribue)

  val statusReadByPro = Seq(
    Transmis,
    ConsulteIgnore
  ) ++ statusWithProResponse

  val statusOngoing = List(TraitementEnCours, Transmis)

  def hasResponse(report: Report): Boolean = statusWithProResponse.contains(report.status)

  implicit class ReportStatusOps(reportStatus: ReportStatus) {
    def isFinal: Boolean =
      Seq(MalAttribue, ConsulteIgnore, NonConsulte, Infonde, PromesseAction, InformateurInterne, NA, SuppressionRGPD)
        .contains(
          reportStatus
        )

    def isNotFinal = !isFinal
  }

  def translate(status: ReportStatus, userRole: UserRole): String = {
    def isPro = userRole == UserRole.Professionnel
    status match {
      case NA                 => if (isPro) "" else "Non transmis"
      case InformateurInterne => if (isPro) "" else "Informateur interne"
      case TraitementEnCours  => if (isPro) "Non consulté" else "Traitement en cours"
      case Transmis           => if (isPro) "À répondre" else "Signalement transmis"
      case PromesseAction     => if (isPro) "Clôturé" else "Promesse action"
      case Infonde            => if (isPro) "Clôturé" else "Signalement infondé"
      case NonConsulte        => if (isPro) "Clôturé" else "Signalement non consulté"
      case ConsulteIgnore     => if (isPro) "Clôturé" else "Signalement consulté ignoré"
      case MalAttribue        => if (isPro) "Clôturé" else "Signalement mal attribué"
      case SuppressionRGPD    => if (isPro) "Supprimé" else "Suppression RGPD"
    }
  }

  def fromResponseType(reportResponseType: ReportResponseType) =
    reportResponseType match {
      case ReportResponseType.ACCEPTED      => ReportStatus.PromesseAction
      case ReportResponseType.REJECTED      => ReportStatus.Infonde
      case ReportResponseType.NOT_CONCERNED => ReportStatus.MalAttribue
    }
}
