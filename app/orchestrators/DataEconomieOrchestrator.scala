package orchestrators

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import io.scalaland.chimney.dsl._
import models.dataeconomie.ReportDataEconomie
import models.report.ReportTag.ReportTagTranslationOps
import play.api.Logger
import repositories.report.ReportRepositoryInterface

import scala.concurrent.ExecutionContext

class DataEconomieOrchestrator(
    reportRepository: ReportRepositoryInterface
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  def getReportDataEconomie(): Source[ReportDataEconomie, NotUsed] =
    reportRepository.streamReports
      .map(
        _.into[ReportDataEconomie]
          .withFieldComputed(_.companyNumber, _.companyAddress.number)
          .withFieldComputed(_.companyStreet, _.companyAddress.street)
          .withFieldComputed(_.companyAddressSupplement, _.companyAddress.addressSupplement)
          .withFieldComputed(_.companyCity, _.companyAddress.city)
          .withFieldComputed(_.companyCountry, _.companyAddress.country)
          .withFieldComputed(_.companyPostalCode, _.companyAddress.postalCode)
          .withFieldComputed(_.tags, _.tags.map(_.translate()))
          .withFieldComputed(_.activityCode, _.companyActivityCode)
          .transform
      )
}
