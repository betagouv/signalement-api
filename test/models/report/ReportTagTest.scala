package models.report

import controllers.error.AppError.InvalidReportTagBody
import controllers.error.AppError.InvalidTagBody
import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragments

import java.util.UUID
import scala.util.Failure
import scala.util.Try

class ReportTagTest extends Specification {

  "ReportTagTest" should {

    Fragments.foreach(ReportTag.values) { v =>
      s"parse from entry name ${v.entryName}" in {
        ReportTagFilter.withName(v.entryName) shouldEqual v
      }
    }

    s"fail to parse invalid tag " in {
      val invalidName = UUID.randomUUID().toString
      Try(ReportTagFilter.withName(invalidName)) shouldEqual Failure(InvalidTagBody(invalidName))
    }

    s"fail to parse invalid NA  report tag " in {
      Try(ReportTag.withName(ReportTagFilter.NA.entryName)) shouldEqual Failure(
        InvalidReportTagBody(ReportTagFilter.NA.entryName)
      )
    }

  }
}
