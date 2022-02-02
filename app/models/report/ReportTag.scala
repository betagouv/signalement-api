package models.report

import controllers.error.AppError.InvalidReportTagBody
import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed abstract class ReportTag(val displayName: String) extends EnumEntry

object ReportTag extends PlayEnum[ReportTag] {

  val values = findValues

  def fromDisplayOrEntryName(name: String): ReportTag = withDisplayName(name) match {
    case Some(value) => value
    case None        => withNameOption(name).getOrElse(throw InvalidReportTagBody(name))
  }

  private def withDisplayName(displayName: String) = values.find(_.displayName == displayName)

  case object LitigeContractuel extends ReportTag("Litige contractuel")
  case object Hygiene extends ReportTag("hygiène")
  case object ProduitDangereux extends ReportTag("Produit dangereux")
  case object DemarchageADomicile extends ReportTag("Démarchage à domicile")
  case object Ehpad extends ReportTag("Ehpad")
  case object DemarchageTelephonique extends ReportTag("Démarchage téléphonique")
  case object AbsenceDeMediateur extends ReportTag("Absence de médiateur")
  case object Bloctel extends ReportTag("Bloctel")
  case object Influenceur extends ReportTag("Influenceur")
  case object ReponseConso extends ReportTag("ReponseConso")
  case object Internet extends ReportTag("Internet")
  case object ProduitIndustriel extends ReportTag("Produit industriel")
  case object ProduitAlimentaire extends ReportTag("Produit alimentaire")
  case object CompagnieAerienne extends ReportTag("Compagnie aerienne")
  case object NA extends ReportTag("NA")

}
