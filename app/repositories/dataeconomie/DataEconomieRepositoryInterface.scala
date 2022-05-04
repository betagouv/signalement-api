package repositories.dataeconomie
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.google.inject.ImplementedBy
import models.report.Report

@ImplementedBy(classOf[DataEconomieRepository])
trait DataEconomieRepositoryInterface {

  def reports(): Source[Report, NotUsed]
}
