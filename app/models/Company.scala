package models

import java.time.OffsetDateTime
import java.util.UUID
import utils.SIRET

case class Company(
                  id: UUID,
                  siret: SIRET,
                  creationDate: OffsetDateTime,
                  name: String,
                  address: String,
                  postalCode: Option[String],
                )
