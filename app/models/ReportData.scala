package models

import java.util.UUID

case class ReportData(
                       reportId: UUID,
                       readTime: Option[Long],
                       responseTime: Option[Long]
                     )
