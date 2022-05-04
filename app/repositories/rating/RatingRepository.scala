package repositories.rating

import models.Rating
import play.api.db.slick.DatabaseConfigProvider
import repositories.CRUDRepository
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class RatingRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[RatingTable, Rating]
    with RatingRepositoryInterface {

  override val dbConfig = dbConfigProvider.get[JdbcProfile]

  override val table = RatingTable.table

}
