package repositories.reportfile

import models.report.ReportFileOrigin
import repositories.PostgresProfile.api._

object ReportFileColumnType {

  implicit val ReportFileOriginColumnType =
    MappedColumnType.base[ReportFileOrigin, String](_.value, ReportFileOrigin(_))

}
