package models.consumerconsent

import utils.EmailAddress

import java.util.UUID

case class ConsumerConsent(
    id: ConsumerConsentId,
    email: EmailAddress
)

object ConsumerConsent {
  def create(email: EmailAddress): ConsumerConsent =
    ConsumerConsent(ConsumerConsentId(UUID.randomUUID()), email)
}
