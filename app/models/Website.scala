package models

import java.time.OffsetDateTime
import java.util.UUID
import utils.URL

case class Website(
  id: UUID,
  creationDate: OffsetDateTime,
  URL: URL,
  companyId: Option[UUID]
)
