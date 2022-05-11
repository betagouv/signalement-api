package repositories.rating

import models.Rating
import repositories.CRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class RatingRepository @Inject() (override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[RatingTable, Rating]
    with RatingRepositoryInterface {

  override val table = RatingTable.table

}
