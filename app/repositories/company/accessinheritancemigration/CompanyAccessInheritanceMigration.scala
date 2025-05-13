package repositories.company.accessinheritancemigration

import java.time.OffsetDateTime
import java.util.UUID

case class CompanyAccessInheritanceMigration(
    companyId: UUID,
    processedAt: OffsetDateTime
)
