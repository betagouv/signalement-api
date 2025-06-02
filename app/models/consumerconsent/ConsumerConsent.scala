package models.consumerconsent

import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

case class ConsumerConsent(
    id: ConsumerConsentId,
    email: EmailAddress,
    creationDate: OffsetDateTime,
    deletionDate: Option[OffsetDateTime]
)

object ConsumerConsent {
  def create(email: EmailAddress): ConsumerConsent =
    ConsumerConsent(
      ConsumerConsentId(UUID.randomUUID()),
      email,
      OffsetDateTime.now(),
      None
    )
}
