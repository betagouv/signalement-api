package models

import play.api.libs.json.{Json, OFormat}

case class Signalement (
                       typeEtablissement: String,
                       categorieAnomalie: String,
                       precisionAnomalie: String,
                       nomEtablissement: String,
                       adresseEtablissement: String,
                       description: Option[String],
                       prenom: String,
                       nom: String,
                       email: String,
                       photo: Option[String]
                       )
object Signalement {

  implicit val signalementFormat: OFormat[Signalement] = Json.format[Signalement]

}