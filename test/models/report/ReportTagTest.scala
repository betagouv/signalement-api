package models.report

import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragments

class ReportTagTest extends Specification {

  "ReportTagTest" should {

    Fragments.foreach(ReportTag.values) { v =>
      s"parse from entry name ${v.entryName}" in {
        ReportTag.fromDisplayOrEntryName(v.entryName) shouldEqual v
      }

      s"parse from display name ${v.displayName}" in {
        ReportTag.fromDisplayOrEntryName(v.displayName) shouldEqual v
      }
    }

  }
}
