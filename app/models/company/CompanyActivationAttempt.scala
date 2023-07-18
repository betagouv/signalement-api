package models.company

import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID
case class CompanyActivationAttempt(
    id: UUID,
    siret: String,
    timestamp: OffsetDateTime
)
object CompanyActivationAttempt {
  def build(siret: SIRET) =
    CompanyActivationAttempt(
      UUID.randomUUID,
      siret.value,
      OffsetDateTime.now()
    )
}
