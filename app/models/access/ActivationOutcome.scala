package models.access

object ActivationOutcome extends Enumeration {
  type ActivationOutcome = Value
  val Success, NotFound, EmailConflict = Value
}
