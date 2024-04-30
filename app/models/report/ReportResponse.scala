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
      case ResponseDetails.REMBOURSEMENT => "procéder à un remboursement ou un avoir"
      case ResponseDetails.REPARATION_OU_REMPLACEMENT =>
        "procéder à une réparation ou au remplacement du produit défectueux"
      case ResponseDetails.LIVRAISON                        => "procéder à la livraison du bien ou du service"
      case ResponseDetails.CONSEIL_D_UTILISATION            => "apporter un conseil d’utilisation"
      case ResponseDetails.ME_CONFORMER_A_LA_REGLEMENTATION => "se conformer à la réglementation en vigueur"
      case ResponseDetails.ADAPTER_MES_PRATIQUES            => "adapter mes pratiques"
      case ResponseDetails.TRANSMETTRE_AU_SERVICE_COMPETENT => ""
      case ResponseDetails.DEMANDE_DE_PLUS_D_INFORMATIONS   => ""
      case ResponseDetails.RESILIATION                      => ""
      case ResponseDetails.AUTRE                            => reportResponse.otherResponseDetails.getOrElse("")
      case _                                                => ""
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

  final case object REMBOURSEMENT                    extends ResponseDetails
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
