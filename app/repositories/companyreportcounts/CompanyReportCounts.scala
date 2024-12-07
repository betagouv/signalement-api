package repositories.companyreportcounts

import java.util.UUID

case class CompanyReportCounts(
    companyId: UUID,
    totalReports: Long,
    totalProcessedReports: Long
)
