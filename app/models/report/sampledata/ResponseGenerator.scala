package models.report.sampledata

import models.report.ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR
import models.report.IncomingReportResponse
import models.report.ReportResponseType.ACCEPTED
import models.report.ReportResponseType.NOT_CONCERNED
import models.report.ReportResponseType.REJECTED

object ResponseGenerator {

  def acceptedResponse() =
    IncomingReportResponse(
      responseType = ACCEPTED,
      consumerDetails =
        "Madame, Monsieur, Dès réception de ce signalement, j'ai donné consigne à mon équipe de faire le point sur le dossier de Madame DANLAT POBISSONT. Nous lui avons confirmé sur message répondeur la résiliation immédiate de même que le remboursement de 40 €. Bien cordialement à vous, Georges Grosnez ",
      dgccrfDetails = Some(
        "Délai de livraison allongé cause pic de fin d'année des transporteurs, puis report à la demande du client. Geste commercial financier important proposé au client pour compenser en partie sa mauvaise expérience."
      ),
      fileIds = List.empty,
      responseDetails = Some(REMBOURSEMENT_OU_AVOIR)
    )

  def rejectedResponse() =
    IncomingReportResponse(
      responseType = REJECTED,
      consumerDetails =
        "Monsieur, L'application de ces \"trajets le plus cher\" a été effectué par la société circulée. Notre Service Clients Grosbilli a bien contacté la société CARIBOU pour qu'elle analyse votre situation et corrige le cas échéant. Votre dossier est toujours en cours auprès de cette société. Notre Service Clients Grosbilli vous tiendra au courant de leur réponse dès que nous l'aurons. Cordialement, La Direction Clientèle Grosbilli",
      dgccrfDetails = None,
      fileIds = List.empty,
      responseDetails = None
    )

  def notConcernedResponse() =
    IncomingReportResponse(
      responseType = NOT_CONCERNED,
      consumerDetails =
        "Bonjour, pouvez-vous m'en dire plus sir l'entreprise car au début il se présente bureau d'étude et après il passe sur Jfdi je pense pas que que ca vienne de nous impossible ces n'ai pas la politique de la maison et nous travaillons que sur demande du client, si vous avez le numéro de cette individu pourriez-vous me le faire parvenir merci ",
      dgccrfDetails = None,
      fileIds = List.empty,
      responseDetails = None
    )

}
