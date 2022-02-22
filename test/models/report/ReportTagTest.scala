package models.report

import controllers.error.AppError.InvalidReportTagBody
import controllers.error.AppError.InvalidTagBody
import models.report.ReportTag
import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragments
import play.api.libs.json.Json
import models.report.ReportTag.ReportTagTranslationOps
import models.report.ReportTag.jsonFormat

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
      Try(ReportTag.withName(invalidName)) shouldEqual Failure(InvalidTagBody(invalidName))
    }

    s"fail to parse invalid report tag " in {
      val invalidName = UUID.randomUUID().toString
      Try(ReportTag.withName(invalidName)) shouldEqual Failure(InvalidReportTagBody(invalidName))
    }
  }
}
