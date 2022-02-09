package models.report

import controllers.error.AppError.InvalidReportTagBody
import controllers.error.AppError.InvalidTagBody
import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed trait Tag extends EnumEntry

sealed trait ReportTag extends Tag

object Tag extends PlayEnum[Tag] {

  val values: IndexedSeq[Tag] = findValues

  /** No tag associated to report, used only for filtering
    */
  case object NA extends Tag

  override def withName(name: String): Tag =
    Tag.withNameOption(name).collect { case r: Tag => r }.getOrElse(throw InvalidTagBody(name))

  object ReportTag extends PlayEnum[ReportTag] {

    val values: IndexedSeq[ReportTag] = findValues

    override def withName(name: String): ReportTag =
      ReportTag.withNameOption(name).collect { case r: ReportTag => r }.getOrElse(throw InvalidReportTagBody(name))

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

    def reportTagFrom(tags: Seq[Tag]): Seq[ReportTag] = tags.collect { case r: ReportTag => r }

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
      }

    }

  }

}
