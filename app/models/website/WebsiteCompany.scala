package models.website

import models.Company
import models.WebsiteKind

import java.time.OffsetDateTime
import java.util.UUID

case class WebsiteCompany(
    id: UUID,
    creationDate: OffsetDateTime,
    host: String,
    companyId: UUID,
    kind: WebsiteKind,
    company: Option[Company]
)
