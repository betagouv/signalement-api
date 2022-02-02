package models.report

import controllers.error.AppError.InvalidReportTagBody
import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed abstract class ReportTag extends EnumEntry

object ReportTag extends PlayEnum[ReportTag] {

  val values = findValues

  override def withName(name: String): ReportTag = withNameOption(name).getOrElse(throw InvalidReportTagBody(name))

  case object LitigeContractuel extends ReportTag
  case object Hygiene extends ReportTag
  case object ProduitDangereux extends ReportTag
  case object DemarchageADomicile extends ReportTag
  case object Ehpad extends ReportTag
  case object DemarchageTelephonique extends ReportTag
  case object AbsenceDeMediateur extends ReportTag
  case object Bloctel extends ReportTag
  case object Influenceur extends ReportTag
  case object ReponseConso extends ReportTag
  case object Internet extends ReportTag
  case object ProduitIndustriel extends ReportTag
  case object ProduitAlimentaire extends ReportTag
  case object CompagnieAerienne extends ReportTag
  case object NA extends ReportTag

  implicit class ReportTagTranslationOps(reportTag: ReportTag) {
    def translate(): String = reportTag match {
      case LitigeContractuel      => "Litige contractuel"
      case Hygiene                => "hygiène"
      case ProduitDangereux       => "Produit dangereux"
      case DemarchageADomicile    => "Démarchage à domicile"
      case Ehpad                  => "Ehpad"
      case DemarchageTelephonique => "Démarchage téléphonique"
      case AbsenceDeMediateur     => "Absence de médiateur"
      case Bloctel                => "Bloctel"
      case Influenceur            => "Influenceur"
      case ReponseConso           => "ReponseConso"
      case Internet               => "Internet"
      case ProduitIndustriel      => "Produit industriel"
      case ProduitAlimentaire     => "Produit alimentaire"
      case CompagnieAerienne      => "Compagnie aerienne"
      case NA                     => "NA"
    }
  }

}
