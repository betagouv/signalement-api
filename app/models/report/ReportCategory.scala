package models.report

import controllers.error.AppError.MalformedValue
import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed abstract class ReportCategory(val label: String, val legacy: Boolean = false) extends EnumEntry

object ReportCategory extends PlayEnum[ReportCategory] {

  case object RetraitRappelSpecifique  extends ReportCategory("Retrait-Rappel spécifique")
  case object Coronavirus              extends ReportCategory("COVID-19 (coronavirus)")
  case object CafeRestaurant           extends ReportCategory("Café / Restaurant")
  case object AchatMagasinLegacy       extends ReportCategory("Achat / Magasin", legacy = true)
  case object AchatMagasinInternet     extends ReportCategory("Achat (Magasin ou Internet)", legacy = true)
  case object AchatMagasin             extends ReportCategory("Achat en Magasin")
  case object AchatInternet            extends ReportCategory("Achat sur internet")
  case object ServicesAuxParticuliers  extends ReportCategory("Services aux particuliers")
  case object TelEauGazElec            extends ReportCategory("Téléphonie / Eau-Gaz-Electricité", legacy = true)
  case object EauGazElectricite        extends ReportCategory("Eau / Gaz / Electricité")
  case object TelephonieFaiMedias      extends ReportCategory("Téléphonie / Fournisseur d'accès internet / médias")
  case object BanqueAssuranceMutuelle  extends ReportCategory("Banque / Assurance / Mutuelle")
  case object IntoxicationAlimentaire  extends ReportCategory("Intoxication alimentaire")
  case object ProduitsObjets           extends ReportCategory("Produits / Objets", legacy = true)
  case object Internet                 extends ReportCategory("Internet (hors achats)")
  case object TravauxRenovations       extends ReportCategory("Travaux / Rénovation")
  case object VoyageLoisirs            extends ReportCategory("Voyage / Loisirs")
  case object Immobilier               extends ReportCategory("Immobilier")
  case object Sante                    extends ReportCategory("Secteur de la santé")
  case object VoitureVehicule          extends ReportCategory("Voiture / Véhicule", legacy = true)
  case object Animaux                  extends ReportCategory("Animaux")
  case object DemarchesAdministratives extends ReportCategory("Démarches administratives")
  case object VoitureVehiculeVelo      extends ReportCategory("Voiture / Véhicule / Vélo")
  case object DemarchageAbusif         extends ReportCategory("Démarchage abusif")

  def fromValue(v: String): ReportCategory = withNameOption(v).fold(throw MalformedValue(v, "ReportCategory"))(identity)

  override def values: IndexedSeq[ReportCategory] = findValues

  def displayValue(v: String): String = withNameOption(v).fold(v)(_.label)
}
