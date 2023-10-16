package models.report.signalconsoreview

import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry
import play.api.libs.json._

sealed abstract class SignalConsoEvaluation(val value: Int) extends IntEnumEntry

object SignalConsoEvaluation extends IntEnum[SignalConsoEvaluation] {

  case object One   extends SignalConsoEvaluation(1)
  case object Two   extends SignalConsoEvaluation(2)
  case object Three extends SignalConsoEvaluation(3)
  case object Four  extends SignalConsoEvaluation(4)
  case object Five  extends SignalConsoEvaluation(5)

  val values: IndexedSeq[SignalConsoEvaluation] = findValues

  implicit val jsonFormat: Format[SignalConsoEvaluation] = Format(
    Reads.IntReads.map(SignalConsoEvaluation.withValue),
    Writes.IntWrites.contramap(_.value)
  )
}
