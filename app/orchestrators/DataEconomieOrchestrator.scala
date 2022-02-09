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
      .map(x =>
        x._1
          .into[ReportDataEconomie]
          .withFieldComputed(_.companyNumber, _.companyAddress.number)
          .withFieldComputed(_.companyStreet, _.companyAddress.street)
          .withFieldComputed(_.companyAddressSupplement, _.companyAddress.addressSupplement)
          .withFieldComputed(_.companyCity, _.companyAddress.city)
          .withFieldComputed(_.companyCountry, _.companyAddress.country)
          .withFieldComputed(_.companyPostalCode, _.companyAddress.postalCode)
          .withFieldComputed(_.tags, _.tags.map(_.translate()))
          .withFieldConst(_.activityCode, x._2.flatMap(_.activityCode))
          .transform
      )
}
