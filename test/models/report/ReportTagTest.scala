package models.report

import controllers.error.AppError.InvalidReportTagBody
import controllers.error.AppError.InvalidTagBody
import models.report.Tag.ReportTag
import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragments
import play.api.libs.json.Json
import models.report.Tag.ReportTag.ReportTagTranslationOps
import models.report.Tag.ReportTag.jsonFormat

import java.util.UUID
import scala.util.Failure
import scala.util.Try

class ReportTagTest extends Specification {

  "ReportTagTest" should {

    Fragments.foreach(ReportTag.values) { v =>
      s"parse from entry name ${v.entryName}" in {
        v shouldEqual (Json.toJson(v.entryName).as[ReportTag])
      }

      s"parse from translated name ${v.translate()}" in {
        v shouldEqual (Json.toJson(v.translate()).as[ReportTag](ReportTag.TranslationReportTagReads))
      }

      s"parse with TranslationReportTagReads from entry name ${v.translate()}" in {
        v shouldEqual (Json.toJson(v.entryName).as[ReportTag](ReportTag.TranslationReportTagReads))
      }
    }

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
