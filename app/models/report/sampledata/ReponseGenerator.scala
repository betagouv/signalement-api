package models.report.sampledata

import models.report.ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR
import models.report.IncomingReportResponse
import models.report.ReportResponseType.ACCEPTED
import models.report.ReportResponseType.NOT_CONCERNED
import models.report.ReportResponseType.REJECTED

object ReponseGenerator {

  def acceptedResponse() =
    IncomingReportResponse(
      responseType = ACCEPTED,
      consumerDetails = "Consumer details",
      dgccrfDetails = Some("CCRF details"),
      fileIds = List.empty,
      responseDetails = Some(REMBOURSEMENT_OU_AVOIR)
    )

  def rejectedResponse() =
    IncomingReportResponse(
      responseType = REJECTED,
      consumerDetails = "Consumer details",
      dgccrfDetails = Some("CCRF details"),
      fileIds = List.empty,
      responseDetails = None
    )

  def notConcernedResponse() =
    IncomingReportResponse(
      responseType = NOT_CONCERNED,
      consumerDetails = "Consumer details",
      dgccrfDetails = Some("CCRF details"),
      fileIds = List.empty,
      responseDetails = None
    )

}
