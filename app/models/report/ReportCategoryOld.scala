package models.report

import enumeratum.EnumEntry
import enumeratum.PlayEnum

sealed abstract class ReportCategoryOld(value: String, legacy: Boolean = false) extends EnumEntry

object ReportCategoryOld extends PlayEnum[ReportCategoryOld] {

  val values = findValues

  case object RetraitRappelSpecifique extends ReportCategoryOld("Retrait-Rappel pécifique")
  case object Covid extends ReportCategoryOld("COVID-19 (coronavirus)")
  case object CafeRestaurant extends ReportCategoryOld("Café / Restaurant")
  case object AchatMagasin extends ReportCategoryOld("Achat / Magasin", legacy = true)
  case object AchatMagasinInternet extends ReportCategoryOld("Achat (Magasin ou Internet)")
  case object Service extends ReportCategoryOld("Services aux particuliers")
  case object TelEauGazElec extends ReportCategoryOld("Téléphonie / Eau-Gaz-Electricité", legacy = true)
  case object EauGazElec extends ReportCategoryOld("Eau / Gaz / Electricité")
  case object TelFaiMedias extends ReportCategoryOld("Téléphonie / Fournisseur d'accès internet / médias")
  case object BanqueAssuranceMutuelle extends ReportCategoryOld("Banque / Assurance / Mutuelle")
  case object IntoxicationAlimentaire extends ReportCategoryOld("Intoxication alimentaire")
  case object ProduitsObjets extends ReportCategoryOld("Produits / Objets", legacy = true)
  case object Internet extends ReportCategoryOld("Internet (hors achats)")
  case object TravauxRenovations extends ReportCategoryOld("Travaux / Rénovation")
  case object VoyageLoisirs extends ReportCategoryOld("Voyage / Loisirs")
  case object Immobilier extends ReportCategoryOld("Immobilier")
  case object Sante extends ReportCategoryOld("Secteur de la santé")
  case object VoitureVehicule extends ReportCategoryOld("Voiture / Véhicule", legacy = true)
  case object Animaux extends ReportCategoryOld("Animaux")
  case object DemarchesAdministratives extends ReportCategoryOld("Démarches administratives")
  case object VoitureVehiculeVelo extends ReportCategoryOld("Voiture / Véhicule / Vélo")
  case object DemarchageAbusif extends ReportCategoryOld("Démarchage abusif")

}
