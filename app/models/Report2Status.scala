package models

import enumeratum._

sealed trait Report2Status extends EnumEntry

object Report2Status extends PlayEnum[Report2Status] {

  val values = findValues

  case object NA extends Report2Status
  case object LanceurAlerte extends Report2Status
  case object TraitementEnCours extends Report2Status
  case object Transmis extends Report2Status
  case object PromesseAction extends Report2Status
  case object Infonde extends Report2Status
  case object NonConsulte extends Report2Status
  case object ConsulteIgnore extends Report2Status
  case object MalAttribue extends Report2Status

  def isFinal(status: Report2Status): Boolean =
    Seq(MalAttribue, ConsulteIgnore, NonConsulte, Infonde, PromesseAction, LanceurAlerte, NA).contains(status)

  def translate(status: Report2Status, userRole: UserRole): String = {
    def isPro = userRole == UserRoles.Pro
    def isDGCCRF = userRole == UserRoles.DGCCRF
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
