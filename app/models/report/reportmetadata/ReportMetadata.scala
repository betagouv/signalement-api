package models.report.reportmetadata

import models.report.Report

import java.util.UUID

case class ReportMetadata(
    reportId: UUID,
    isMobileApp: Boolean,
    os: Option[Os],
    assignedUserId: Option[UUID]
)

case class ReportWithMetadata(
    report: Report,
    metadata: Option[ReportMetadata]
)
