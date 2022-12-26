package models.report

import controllers.error.AppError.MalformedValue
import models.report.ReportCategory._
import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragments
import play.api.libs.json.Json

import java.util.UUID

class ReportCategoryTest extends Specification {

  "ReportTagTest" should {

    Fragments.foreach(
      ReportCategory.values
    ) { v =>
      s"parse json from value ${v.entryName}" in {
        v shouldEqual (Json.toJson(v).as[ReportCategory])
      }

      s"retreive from entryName ${v.entryName}" in {
        v shouldEqual fromValue(v.entryName)
      }

    }

    "Failed when passing unvalid entryName" in {
      fromValue(UUID.randomUUID().toString) must throwA[MalformedValue]
    }

  }

}
