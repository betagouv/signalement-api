package repositories.reportresponse

import models.report.ExistingResponseDetails
import models.report.ReportResponseType

import java.time.OffsetDateTime
import java.util.UUID

case class ReportResponse(
    eventId: UUID,
    reportId: Option[UUID],
    userId: Option[UUID],
    companyId: Option[UUID],
    creationDate: OffsetDateTime,
    responseType: ReportResponseType,
    responseDetails: Option[ExistingResponseDetails],
    consumerDetails: Option[String],
    dgccrfDetails: Option[String]
)
