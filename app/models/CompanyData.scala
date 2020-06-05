package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import utils.{Address, SIRET}

case class CompanyData (
                         id: UUID,
                         siret: String,
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
                         denominationUsuelleEtablissement: Option[String]
                       ) {

  def toSearchResult = CompanySearchResult(SIRET(siret), denominationUsuelleEtablissement, libelleVoieEtablissement.map(Address(_)), codePostalEtablissement)
}


case class CompanySearchResult (
                    siret: SIRET,
                    name: Option[String],
                    address: Option[Address],
                    postalCode: Option[String]
                  )

object CompanySearchResult {
  implicit val format: OFormat[CompanySearchResult] = Json.format[CompanySearchResult]
}