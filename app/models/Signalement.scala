package models

import java.time.LocalDate
import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Signalement (
                         id: UUID,
                         typeEtablissement: String,
                         categorieAnomalie: String,
                         precisionAnomalie: String,
                         nomEtablissement: String,
                         adresseEtablissement: String,
                         dateConstat: LocalDate,
                         heureConstat: Option[Int],
                         description: Option[String],
                         prenom: String,
                         nom: String,
                         email: String,
                         accordContact: Boolean,
                         ticketFileId: Option[Long],
                         anomalieFileId: Option[Long]
                       )
object Signalement {

  implicit val signalementFormat: OFormat[Signalement] = Json.format[Signalement]

}