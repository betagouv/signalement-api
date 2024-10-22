package repositories.proconnect

import java.time.OffsetDateTime
import java.util.UUID

case class ProConnectSession(
    id: UUID,
    state: String,
    creationDate: OffsetDateTime
)

object ProConnectSession {
  def apply(state: String) = new ProConnectSession(UUID.randomUUID(), state, OffsetDateTime.now())
}
