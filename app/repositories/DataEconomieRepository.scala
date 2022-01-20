package repositories

import akka.actor.ActorSystem
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.alpakka.slick.scaladsl.SlickSession

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
      .source(ReportTables.tables.joinLeft(CompanyTables.tables).on(_.companyId === _.id).result)
      .log("user")

}
