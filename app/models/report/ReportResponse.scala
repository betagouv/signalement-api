package models.report

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import models.report.reportfile.ReportFileId
import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class ReportResponse(
    responseType: ReportResponseType,
    consumerDetails: String,
    dgccrfDetails: Option[String],
    fileIds: List[ReportFileId],
    responseDetails: Option[ResponseDetails],
    otherResponseDetails: Option[String]
)

object ReportResponse {
  implicit val reportResponse: OFormat[ReportResponse] = Json.format[ReportResponse]

  def translateResponseDetails(reportResponse: ReportResponse): Option[String] =
    reportResponse.responseDetails.map {
      case ResponseDetails.REMBOURSEMENT_OU_AVOIR => "Je vais procéder à un remboursement ou un avoir"
      case ResponseDetails.REPARATION_OU_REMPLACEMENT =>
        "Je vais procéder à une réparation ou au remplacement du produit défectueux"
      case ResponseDetails.LIVRAISON                        => "Je vais procéder à la livraison du bien ou du service"
      case ResponseDetails.CONSEIL_D_UTILISATION            => "Je souhaite apporter un conseil d’utilisation"
      case ResponseDetails.ME_CONFORMER_A_LA_REGLEMENTATION => "Je vais me conformer à la réglementation en vigueur"
      case ResponseDetails.ADAPTER_MES_PRATIQUES            => "Je vais adapter mes pratiques"
      case ResponseDetails.TRANSMETTRE_AU_SERVICE_COMPETENT => "Je vais transmettre la demande au service compétent"
      case ResponseDetails.DEMANDE_DE_PLUS_D_INFORMATIONS =>
        "Je vais demander davantage d’informations au consommateur afin de lui apporter une réponse"
      case ResponseDetails.RESILIATION              => "Je vais procéder à la résiliation du contrat"
      case ResponseDetails.PRATIQUE_LEGALE          => "La pratique signalée est légale"
      case ResponseDetails.PRATIQUE_N_A_PAS_EU_LIEU => "La pratique signalée n’a pas eu lieu"
      case ResponseDetails.MAUVAISE_INTERPRETATION  => "Il s’agit d’une mauvaise interprétation des faits"
      case ResponseDetails.DEJA_REPONDU             => "J’ai déjà répondu à ce consommateur sur la plateforme"
      case ResponseDetails.TRAITEMENT_EN_COURS =>
        "Le traitement de ce cas est déjà en cours auprès de notre service client"
      case ResponseDetails.PARTENAIRE_COMMERCIAL     => "Il concerne un de nos partenaires commerciaux / vendeurs tiers"
      case ResponseDetails.ENTREPRISE_DU_MEME_GROUPE => "Il concerne une entreprise du même groupe"
      case ResponseDetails.HOMONYME                  => "Il concerne une société homonyme"
      case ResponseDetails.ENTREPRISE_INCONNUE       => "Il concerne une société que je ne connais pas"
      case ResponseDetails.USURPATION                => "Il s’agit d’une usurpation d’identité professionnelle"
      case ResponseDetails.AUTRE                     => reportResponse.otherResponseDetails.getOrElse("")
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

sealed trait ResponseDetails extends EnumEntry

object ResponseDetails extends PlayEnum[ResponseDetails] {

  override def values: IndexedSeq[ResponseDetails] = findValues

  final case object REMBOURSEMENT_OU_AVOIR           extends ResponseDetails
  final case object REPARATION_OU_REMPLACEMENT       extends ResponseDetails
  final case object LIVRAISON                        extends ResponseDetails
  final case object CONSEIL_D_UTILISATION            extends ResponseDetails
  final case object ME_CONFORMER_A_LA_REGLEMENTATION extends ResponseDetails
  final case object ADAPTER_MES_PRATIQUES            extends ResponseDetails
  final case object TRANSMETTRE_AU_SERVICE_COMPETENT extends ResponseDetails
  final case object DEMANDE_DE_PLUS_D_INFORMATIONS   extends ResponseDetails
  final case object RESILIATION                      extends ResponseDetails

  final case object PRATIQUE_LEGALE          extends ResponseDetails
  final case object PRATIQUE_N_A_PAS_EU_LIEU extends ResponseDetails
  final case object MAUVAISE_INTERPRETATION  extends ResponseDetails
  final case object DEJA_REPONDU             extends ResponseDetails
  final case object TRAITEMENT_EN_COURS      extends ResponseDetails

  final case object PARTENAIRE_COMMERCIAL     extends ResponseDetails
  final case object ENTREPRISE_DU_MEME_GROUPE extends ResponseDetails
  final case object HOMONYME                  extends ResponseDetails
  final case object ENTREPRISE_INCONNUE       extends ResponseDetails
  final case object USURPATION                extends ResponseDetails

  final case object AUTRE extends ResponseDetails
}
