package models.report.reportmetadata

import java.util.UUID

case class ReportMetadata(
    reportId: UUID,
    isMobileApp: Boolean,
    os: Option[Os]
)
