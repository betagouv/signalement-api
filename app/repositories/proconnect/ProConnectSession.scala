package repositories.proconnect

import java.time.OffsetDateTime
import java.util.UUID

case class ProConnectSession(
    id: UUID,
    state: String,
    nonce: String,
    creationDate: OffsetDateTime
)

object ProConnectSession {
  def apply(state: String, nonce: String) = new ProConnectSession(UUID.randomUUID(), state, nonce, OffsetDateTime.now())
}
