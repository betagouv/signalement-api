package models.report

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.report.ExistingResponseDetails.IncomingResponseDetails
import models.report.reportfile.ReportFileId
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class IncomingReportResponse(
    responseType: ReportResponseType,
    consumerDetails: String,
    dgccrfDetails: Option[String],
    fileIds: List[ReportFileId],
    responseDetails: Option[IncomingResponseDetails]
) {
  def toExisting = ExistingReportResponse(
    responseType = responseType,
    consumerDetails = consumerDetails,
    dgccrfDetails = dgccrfDetails,
    fileIds = fileIds,
    responseDetails = responseDetails,
    otherResponseDetails = None
  )
}

// An already created report response, coming from the DB
// It may contains the legacy value 'AUTRE' that is not allowed anymore
case class ExistingReportResponse(
    responseType: ReportResponseType,
    consumerDetails: String,
    dgccrfDetails: Option[String],
    fileIds: List[ReportFileId],
    responseDetails: Option[ExistingResponseDetails],
    otherResponseDetails: Option[String]
)

object IncomingReportResponse {
  implicit val incomingReportResponse: OFormat[IncomingReportResponse] = Json.format[IncomingReportResponse]

}

object ExistingReportResponse {
  implicit val existingReportResponse: OFormat[ExistingReportResponse] = Json.format[ExistingReportResponse]

  def translateResponseDetails(reportResponse: ExistingReportResponse): Option[String] =
    reportResponse.responseDetails.map {
      case ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR => "Je vais procéder à un remboursement ou un avoir"
      case ExistingResponseDetails.REPARATION_OU_REMPLACEMENT =>
        "Je vais procéder à une réparation ou au remplacement du produit défectueux"
      case ExistingResponseDetails.LIVRAISON             => "Je vais procéder à la livraison du bien ou du service"
      case ExistingResponseDetails.CONSEIL_D_UTILISATION => "Je souhaite apporter un conseil d’utilisation"
      case ExistingResponseDetails.ME_CONFORMER_A_LA_REGLEMENTATION =>
        "Je vais me conformer à la réglementation en vigueur"
      case ExistingResponseDetails.ADAPTER_MES_PRATIQUES => "Je vais adapter mes pratiques"
      case ExistingResponseDetails.TRANSMETTRE_AU_SERVICE_COMPETENT =>
        "Je vais transmettre la demande au service compétent"
      case ExistingResponseDetails.DEMANDE_DE_PLUS_D_INFORMATIONS =>
        "Je vais demander davantage d’informations au consommateur afin de lui apporter une réponse"
      case ExistingResponseDetails.RESILIATION              => "Je vais procéder à la résiliation du contrat"
      case ExistingResponseDetails.PRATIQUE_LEGALE          => "La pratique signalée est légale"
      case ExistingResponseDetails.PRATIQUE_N_A_PAS_EU_LIEU => "La pratique signalée n’a pas eu lieu"
      case ExistingResponseDetails.MAUVAISE_INTERPRETATION  => "Il s’agit d’une mauvaise interprétation des faits"
      case ExistingResponseDetails.DEJA_REPONDU             => "J’ai déjà répondu à ce consommateur sur la plateforme"
      case ExistingResponseDetails.TRAITEMENT_EN_COURS =>
        "Le traitement de ce cas est déjà en cours auprès de notre service client"
      case ExistingResponseDetails.PARTENAIRE_COMMERCIAL =>
        "Il concerne un de nos partenaires commerciaux / vendeurs tiers"
      case ExistingResponseDetails.ENTREPRISE_DU_MEME_GROUPE => "Il concerne une entreprise du même groupe"
      case ExistingResponseDetails.HOMONYME                  => "Il concerne une société homonyme"
      case ExistingResponseDetails.ENTREPRISE_INCONNUE       => "Il concerne une société que je ne connais pas"
      case ExistingResponseDetails.USURPATION                => "Il s’agit d’une usurpation d’identité professionnelle"
      case ExistingResponseDetails.AUTRE                     => reportResponse.otherResponseDetails.getOrElse("")
    }
}

sealed trait ReportResponseType extends EnumEntry

object ReportResponseType extends PlayEnum[ReportResponseType] {

  final case object ACCEPTED      extends ReportResponseType
  final case object REJECTED      extends ReportResponseType
  final case object NOT_CONCERNED extends ReportResponseType

  override def values: IndexedSeq[ReportResponseType] = findValues

  def translate(responseType: ReportResponseType): String =
    responseType match {
      case ACCEPTED      => "Pris en compte"
      case REJECTED      => "Infondé"
      case NOT_CONCERNED => "Mal attribué"
    }
}

// This enum lists all possibles values that
// have already been stored in the DB (so including "Autre")
sealed trait ExistingResponseDetails extends EnumEntry

object ExistingResponseDetails extends PlayEnum[ExistingResponseDetails] {

  override def values: IndexedSeq[ExistingResponseDetails] = findValues
  final case object AUTRE extends ExistingResponseDetails

  // This enum lists only the values that the user can send now
  // (so without "Autre")
  sealed trait IncomingResponseDetails     extends ExistingResponseDetails
  final case object REMBOURSEMENT_OU_AVOIR extends IncomingResponseDetails

  final case object REPARATION_OU_REMPLACEMENT extends IncomingResponseDetails

  final case object LIVRAISON extends IncomingResponseDetails

  final case object CONSEIL_D_UTILISATION extends IncomingResponseDetails

  final case object ME_CONFORMER_A_LA_REGLEMENTATION extends IncomingResponseDetails

  final case object ADAPTER_MES_PRATIQUES extends IncomingResponseDetails

  final case object TRANSMETTRE_AU_SERVICE_COMPETENT extends IncomingResponseDetails

  final case object DEMANDE_DE_PLUS_D_INFORMATIONS extends IncomingResponseDetails

  final case object RESILIATION extends IncomingResponseDetails

  final case object PRATIQUE_LEGALE extends IncomingResponseDetails

  final case object PRATIQUE_N_A_PAS_EU_LIEU extends IncomingResponseDetails

  final case object MAUVAISE_INTERPRETATION extends IncomingResponseDetails

  final case object DEJA_REPONDU extends IncomingResponseDetails

  final case object TRAITEMENT_EN_COURS extends IncomingResponseDetails

  final case object PARTENAIRE_COMMERCIAL extends IncomingResponseDetails

  final case object ENTREPRISE_DU_MEME_GROUPE extends IncomingResponseDetails

  final case object HOMONYME extends IncomingResponseDetails

  final case object ENTREPRISE_INCONNUE extends IncomingResponseDetails

  final case object USURPATION extends IncomingResponseDetails

  object IncomingResponseDetails extends PlayEnum[IncomingResponseDetails] {
    // trick from https://github.com/lloydmeta/enumeratum/issues/296
    val values = ExistingResponseDetails.values.collect { case e: IncomingResponseDetails => e }
  }
}
