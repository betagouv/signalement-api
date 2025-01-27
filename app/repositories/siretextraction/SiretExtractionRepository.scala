package repositories.siretextraction

import models.website.IdentificationStatus
import models.website.Website
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._
import repositories.website.WebsiteColumnType._
import repositories.website.WebsiteTable
import tasks.website.ExtractionResultApi

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait SiretExtractionRepositoryInterface {
  def getByHost(host: String): Future[Option[ExtractionResultApi]]
  def listUnextractedWebsiteHosts(n: Int): Future[List[Website]]
  def insertOrReplace(extraction: ExtractionResultApi): Future[Unit]
}

class SiretExtractionRepository(dbConfig: DatabaseConfig[JdbcProfile])(implicit ec: ExecutionContext)
    extends SiretExtractionRepositoryInterface {

  val table = SiretExtractionTable.table

  import dbConfig._

  override def getByHost(host: String): Future[Option[ExtractionResultApi]] = db.run(
    table.filter(_.host === host).to[List].result.headOption
  )

  def listUnextractedWebsiteHosts(n: Int): Future[List[Website]] = db.run(
    WebsiteTable.table
      .joinLeft(table)
      .on(_.host === _.host)
      .filter(_._1.identificationStatus === (IdentificationStatus.NotIdentified: IdentificationStatus))
      .filter(_._2.isEmpty)
      .map(_._1)
      .distinctOn(_.host)
      .take(n)
      .to[List]
      .result
  )

  override def insertOrReplace(extraction: ExtractionResultApi): Future[Unit] = db.run(
    table.insertOrUpdate(extraction).map(_ => ())
  )
}
