package orchestrators.reportexport

import models.report.{ReportFilter, ReportFilterApi}
import org.specs2.mutable.Specification

import scala.util.Success

class ZipEntryNameTest extends Specification {


  "ZipEntryName" should {

    "remove unsafe char from string" in {

     val res = ZipEntryName.safeString("'/', ':', '*', '?', '\"', '<', '>', '|'")
      res.dropRight(3) shouldEqual ("")
    }

}
}
