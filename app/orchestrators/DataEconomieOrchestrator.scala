package orchestrators

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.scalaland.chimney.dsl.TransformerOps
import models.dataeconomie.ReportDataEconomie
import play.api.Logger
import repositories._

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DataEconomieOrchestrator @Inject() (
    reportRepository: DataEconomieRepository
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  def getReportDataEconomie(): Source[ReportDataEconomie, NotUsed] =
    reportRepository
      .reports()
      .map(
        _.into[ReportDataEconomie]
          .withFieldComputed(_.postalCode, _.companyAddress.postalCode)
          .transform
      )
}
