package repositories.dataeconomie

import akka.actor.ActorSystem
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.alpakka.slick.scaladsl.SlickSession
import repositories.report.ReportTable

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataEconomieRepository @Inject() (
    system: ActorSystem
) {

  implicit val session = SlickSession.forConfig("slick.dbs.default")
  val batchSize = 5000
  system.registerOnTermination(() => session.close())

  import session.profile.api._

  def reports() =
    Slick
      .source(ReportTable.table.result)
      .log("user")

}
