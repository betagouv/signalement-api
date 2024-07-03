package repositories.reportfile

import models.report.ReportFileOrigin

import java.time.OffsetDateTime

case class ReportFileFilter(
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None,
    origin: Option[ReportFileOrigin] = None
)
