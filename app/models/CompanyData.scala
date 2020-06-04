package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import utils.{Address, SIRET}

case class CompanyData (
                         siret: String,
                         denominationUniteLegale: String,
                         dateDernierTraitementEtablissement: String,
                         complementAdresseEtablissement: String,
                         numeroVoieEtablissement: String,
                         indiceRepetitionEtablissement: String,
                         typeVoieEtablissement: String,
                         libelleVoieEtablissement: String,
                         codePostalEtablissement: String,
                         libelleCommuneEtablissement: String,
                         libelleCommuneEtrangerEtablissement: String,
                         distributionSpecialeEtablissement: String,
                         codeCommuneEtablissement: String,
                         codeCedexEtablissement: String,
                         libelleCedexEtablissement: String
                       ) {

  def toSearchResult = CompanySearchResult(SIRET(siret), denominationUniteLegale, Address(libelleVoieEtablissement), Some(codePostalEtablissement))
}


case class CompanySearchResult (
                    siret: SIRET,
                    name: String,
                    address: Address,
                    postalCode: Option[String]
                  )

object CompanySearchResult {
  implicit val format: OFormat[CompanySearchResult] = Json.format[CompanySearchResult]
}