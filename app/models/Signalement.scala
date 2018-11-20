package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Signalement (
                         id: UUID,
                         typeEtablissement: String,
                         categorieAnomalie: String,
                         precisionAnomalie: String,
                         nomEtablissement: String,
                         adresseEtablissement: String,
                         description: Option[String],
                         prenom: String,
                         nom: String,
                         email: String,
                         photoOID: Option[Long]
                       )
object Signalement {

  implicit val signalementFormat: OFormat[Signalement] = Json.format[Signalement]

}