package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import utils.{Address, SIRET}

case class CompanyData (
                         id: UUID,
                         siret: String,
                         siren: String,
                         dateDernierTraitementEtablissement: String,
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
                         activitePrincipaleEtablissement: String
                       ) {

  def voie = Option(Seq(numeroVoieEtablissement, typeVoieEtablissement, libelleVoieEtablissement).flatten).filterNot(_.isEmpty).map(_.reduce((a1, a2) => s"$a1 $a2"))

  def commune = Option(Seq(codePostalEtablissement, libelleCommuneEtablissement).flatten).filterNot(_.isEmpty).map(_.reduce((a1, a2) => s"$a1 $a2"))

  def toSearchResult = CompanySearchResult(
    SIRET(siret),
    denominationUsuelleEtablissement,
    Option(Seq(voie, complementAdresseEtablissement, commune).flatten).filterNot(_.isEmpty).map(_.reduce((a1, a2) => s"$a1 - $a2")).map(Address(_)),
    codePostalEtablissement,
    activitePrincipaleEtablissement
  )

}

case class CompanyUnitData (
                         id: UUID,
                         siren: String,
                         denominationUniteLegale: Option[String]
                       )


case class CompanySearchResult (
                                 siret: SIRET,
                                 name: Option[String],
                                 address: Option[Address],
                                 postalCode: Option[String],
                                 activityLabel: String
                               )

object CompanySearchResult {
  implicit val format: OFormat[CompanySearchResult] = Json.format[CompanySearchResult]
}