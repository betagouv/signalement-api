package models

import java.time.OffsetDateTime
import java.util.UUID

case class Company(
                  id: UUID,
                  siret: String,
                  creationDate: OffsetDateTime,
                  name: String,
                  address: String,
                  postalCode: Option[String],
                )
