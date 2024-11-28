package repositories.albert

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait AlbertClassificationRepositoryInterface {
  def getByReportId(reportId: UUID): Future[Option[AlbertClassification]]
  def createOrUpdate(element: AlbertClassification): Future[AlbertClassification]
}

class AlbertClassificationRepository(dbConfig: DatabaseConfig[JdbcProfile])(implicit ec: ExecutionContext)
    extends AlbertClassificationRepositoryInterface {

  val table = AlbertClassificationTable.table

  import dbConfig._

  def getByReportId(reportId: UUID): Future[Option[AlbertClassification]] = db.run(
    table.filter(_.reportId === reportId).result.headOption
  )

  def createOrUpdate(element: AlbertClassification): Future[AlbertClassification] = db
    .run(
      table.insertOrUpdate(element)
    )
    .map(_ => element)
}
