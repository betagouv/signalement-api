package models.report

import controllers.error.AppError.MalformedValue
import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed trait ReportCategoryStatus
object ReportCategoryStatus {
  case object Legacy
      extends ReportCategoryStatus // it has been modified / splitted / migrated and should never come back
  case object Closed extends ReportCategoryStatus // Old category, not available and no investigation
  case object Inactive
      extends ReportCategoryStatus // Category not available in the website, but an investigation could still happen
  case object Active extends ReportCategoryStatus
}

// Legacy means
// Active means it's an old category not up to date anymore but could come back (COVID is a good example)
sealed abstract class ReportCategory(val label: String, val status: ReportCategoryStatus = ReportCategoryStatus.Active)
    extends EnumEntry

object ReportCategory extends PlayEnum[ReportCategory] {

  case object RetraitRappelSpecifique
      extends ReportCategory("Retrait-Rappel spécifique", status = ReportCategoryStatus.Closed)
  case object Coronavirus        extends ReportCategory("COVID-19 (coronavirus)", status = ReportCategoryStatus.Closed)
  case object CafeRestaurant     extends ReportCategory("Café / Restaurant")
  case object AchatMagasinLegacy extends ReportCategory("Achat / Magasin", status = ReportCategoryStatus.Legacy)
  case object AchatMagasinInternet
      extends ReportCategory("Achat (Magasin ou Internet)", status = ReportCategoryStatus.Legacy)
  case object AchatMagasin            extends ReportCategory("Achat en Magasin")
  case object AchatInternet           extends ReportCategory("Achat sur internet")
  case object ServicesAuxParticuliers extends ReportCategory("Services aux particuliers")
  case object TelEauGazElec
      extends ReportCategory("Téléphonie / Eau-Gaz-Electricité", status = ReportCategoryStatus.Legacy)
  case object EauGazElectricite       extends ReportCategory("Eau / Gaz / Electricité")
  case object TelephonieFaiMedias     extends ReportCategory("Téléphonie / Fournisseur d'accès internet / médias")
  case object BanqueAssuranceMutuelle extends ReportCategory("Banque / Assurance / Mutuelle")
  case object IntoxicationAlimentaire extends ReportCategory("Intoxication alimentaire")
  case object ProduitsObjets          extends ReportCategory("Produits / Objets", status = ReportCategoryStatus.Legacy)
  case object Internet                extends ReportCategory("Internet (hors achats)")
  case object TravauxRenovations      extends ReportCategory("Travaux / Rénovation")
  case object VoyageLoisirs           extends ReportCategory("Voyage / Loisirs")
  case object Immobilier              extends ReportCategory("Immobilier")
  case object Sante                   extends ReportCategory("Secteur de la santé")
  case object VoitureVehicule         extends ReportCategory("Voiture / Véhicule", status = ReportCategoryStatus.Legacy)
  case object Animaux                 extends ReportCategory("Animaux")
  case object DemarchesAdministratives extends ReportCategory("Démarches administratives")
  case object VoitureVehiculeVelo      extends ReportCategory("Voiture / Véhicule / Vélo")
  case object DemarchageAbusif         extends ReportCategory("Démarchage abusif")

  def fromValue(v: String): ReportCategory = withNameOption(v).fold(throw MalformedValue(v, "ReportCategory"))(identity)

  override def values: IndexedSeq[ReportCategory] = findValues

  def displayValue(v: String): String = withNameOption(v).fold(v)(_.label)
}
