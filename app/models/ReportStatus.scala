package models

import enumeratum._

sealed trait ReportStatus2 extends EnumEntry

object ReportStatus2 extends PlayEnum[ReportStatus2] {

  val values = findValues

  case object NA extends ReportStatus2
  case object EmployeeReport extends ReportStatus2
  case object TraitementEnCours extends ReportStatus2
  case object Transmis extends ReportStatus2
  case object PromesseAction extends ReportStatus2
  case object Infonde extends ReportStatus2
  case object NonConsulte extends ReportStatus2
  case object ConsulteIgnore extends ReportStatus2
  case object MalAttribue extends ReportStatus2

  def translate(status: ReportStatus2, userRole: UserRole): String = {
    def isPro = userRole == UserRoles.Pro
    def isDGCCRF = userRole == UserRoles.DGCCRF
    status match {
      case NA => if(isPro) "" else "NA"
      case EmployeeReport => if(isPro) "" else "Lanceur d'alerte"
      case TraitementEnCours => if(isPro) "Non consulté" else "Traitement en cours"
      case Transmis => if(isPro) "À répondre" else if(isDGCCRF) "Traitement en cours" else "Signalement transmis"
      case PromesseAction => if(isPro) "Clôturé" else "Promesse action"
      case Infonde => if(isPro) "Clôturé" else "Signalement infondé"
      case NonConsulte => if(isPro) "Clôturé" else "Signalement non consulté"
      case ConsulteIgnore => if(isPro) "Clôturé" else "Signalement consulté ignoré"
      case MalAttribue => if(isPro) "Clôturé" else "Signalement mal attribué"
    }
  }
}
