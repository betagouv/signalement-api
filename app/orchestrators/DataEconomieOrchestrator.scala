package orchestrators

import io.scalaland.chimney.dsl.TransformerOps
import models.dataeconomie.ReportDataEconomie
import play.api.Logger
import repositories._

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DataEconomieOrchestrator @Inject() (
    reportRepository: ReportRepository
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  def getReportDataEconomie(): Future[List[ReportDataEconomie]] = for {
    reports <- reportRepository.list
    reportDataEconomie = reports.map(
      _.into[ReportDataEconomie]
        .withFieldComputed(_.postalCode, _.companyAddress.postalCode)
        .transform
    )
  } yield reportDataEconomie
}
