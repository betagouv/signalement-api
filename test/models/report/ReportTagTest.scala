package models.report

import controllers.error.AppError.InvalidReportTagBody
import controllers.error.AppError.InvalidTagBody
import models.report.Tag.ReportTag
import org.specs2.mutable.Specification

import java.util.UUID
import scala.util.Failure
import scala.util.Try

class ReportTagTest extends Specification {

  "ReportTagTest" should {

    s"fail to parse invalid report tag " in {
      val invalidName = UUID.randomUUID().toString
      Try(Tag.withName(invalidName)) shouldEqual Failure(InvalidTagBody(invalidName))
    }

    s"fail to parse invalid report tag " in {
      val invalidName = UUID.randomUUID().toString
      Try(ReportTag.withName(invalidName)) shouldEqual Failure(InvalidReportTagBody(invalidName))
    }

    s"fail to parse invalid NA report tag " in {
      Try(ReportTag.withName(Tag.NA.entryName)) shouldEqual Failure(
        InvalidReportTagBody(Tag.NA.entryName)
      )
    }

  }
}
