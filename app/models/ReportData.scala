package models

import java.util.UUID

case class ReportData(
    reportId: UUID,
    readDelay: Option[Double],
    responseDelay: Option[Double]
)
