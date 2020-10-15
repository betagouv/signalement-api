package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import utils.{Address, SIRET}

case class CompanyData (
                         id: UUID,
                         siret: String,
                         siren: String,
                         dateDernierTraitementEtablissement: Option[String],
                         complementAdresseEtablissement: Option[String],
                         numeroVoieEtablissement: Option[String],
                         indiceRepetitionEtablissement: Option[String],
                         typeVoieEtablissement: Option[String],
                         libelleVoieEtablissement: Option[String],
                         codePostalEtablissement: Option[String],
                         libelleCommuneEtablissement: Option[String],
                         libelleCommuneEtrangerEtablissement: Option[String],
                         distributionSpecialeEtablissement: Option[String],
                         codeCommuneEtablissement: Option[String],
                         codeCedexEtablissement: Option[String],
                         libelleCedexEtablissement: Option[String],
                         denominationUsuelleEtablissement: Option[String],
                         enseigne1Etablissement: Option[String],
                         activitePrincipaleEtablissement: String
                       ) {

  def voie =
    Option(Seq(
      numeroVoieEtablissement,
      typeVoieEtablissement.flatMap(typeVoie => TypeVoies.values.find(_._1 == typeVoie).map(_._2.toUpperCase)),
      libelleVoieEtablissement
    ).flatten).filterNot(_.isEmpty).map(_.mkString(" "))


  def commune = Option(Seq(codePostalEtablissement, libelleCommuneEtablissement).flatten).filterNot(_.isEmpty).map(_.mkString(" "))

  def toSearchResult(activityLabel: Option[String]) = CompanySearchResult(
    SIRET(siret),
    denominationUsuelleEtablissement,
    enseigne1Etablissement.filter(Some(_) != denominationUsuelleEtablissement),
    Option(Seq(voie, complementAdresseEtablissement, commune).flatten).filterNot(_.isEmpty).map(_.mkString(" - ")).map(Address(_)),
    codePostalEtablissement,
    activitePrincipaleEtablissement,
    activityLabel
  )

}

case class CompanyActivity (
                             code: String,
                             label: String
                           )


case class CompanySearchResult (
                                 siret: SIRET,
                                 name: Option[String],
                                 brand: Option[String],
                                 address: Option[Address],
                                 postalCode: Option[String],
                                 activityCode: String,
                                 activityLabel: Option[String]
                               )

object CompanySearchResult {
  implicit val format: OFormat[CompanySearchResult] = Json.format[CompanySearchResult]
}

object TypeVoies {
  val values = Seq(
    ("ALL", "Allée"),
    ("AV", "Avenue"),
    ("BD", "Boulevard"),
    ("CAR", "Carrefour"),
    ("CHE", "Chemin"),
    ("CHS", "Chaussée"),
    ("CITE", "Cité"),
    ("COR", "Corniche"),
    ("CRS", "Cours"),
    ("DOM", "Domaine"),
    ("DSC", "Descente"),
    ("ECA", "Ecart"),
    ("ESP", "Esplanade"),
    ("FG", "Faubourg"),
    ("GR", "Grande Rue"),
    ("HAM", "Hameau"),
    ("HLE", "Halle"),
    ("IMP", "Impasse"),
    ("LD", "Lieu dit"),
    ("LOT", "Lotissement"),
    ("MAR", "Marché"),
    ("MTE", "Montée"),
    ("PAS", "Passage"),
    ("PL", "Place"),
    ("PLN", "Plaine"),
    ("PLT", "Plateau"),
    ("PRO", "Promenade"),
    ("PRV", "Parvis"),
    ("QUA", "Quartier"),
    ("QUAI", "Quai"),
    ("RES", "Résidence"),
    ("RLE", "Ruelle"),
    ("ROC", "Rocade"),
    ("RPT", "Rond Point"),
    ("RTE", "Route"),
    ("RUE", "Rue"),
    ("SEN", "Sente - Sentier"),
    ("SQ", "Square"),
    ("TPL", "Terre-plein"),
    ("TRA", "Traverse"),
    ("VLA", "Villa"),
    ("VLGE", "Village")
  )
}