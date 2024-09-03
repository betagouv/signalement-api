package repositories.event

import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType

import java.time.OffsetDateTime

case class EventFilter(
    eventType: Option[EventType] = None,
    action: Option[ActionEventValue] = None,
    start: Option[OffsetDateTime] = None,
    end: Option[OffsetDateTime] = None
)

object EventFilter {
  val Empty = EventFilter()
}
