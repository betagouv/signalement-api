package models.report

import enumeratum._

sealed trait Gender extends EnumEntry {
  val label: String
}

object Gender extends PlayEnum[Gender] {
  val values = findValues

  case object Male extends Gender {
    override val label: String = "Mr "
  }
  case object Female extends Gender {
    override val label: String = "Mme "
  }
}
