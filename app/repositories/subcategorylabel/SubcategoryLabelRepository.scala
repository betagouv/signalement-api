package repositories.subcategorylabel

import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubcategoryLabelRepository(val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    val ec: ExecutionContext
) extends SubcategoryLabelRepositoryInterface {

  import dbConfig._
  val table = SubcategoryLabelTable.table

  override def createOrUpdate(element: SubcategoryLabel): Future[SubcategoryLabel] = db
    .run(
      table.insertOrUpdate(element)
    )
    .map(_ => element)

  override def get(category: String, subcategories: List[String]): Future[Option[SubcategoryLabel]] = db.run(
    table.filter(_.category === category).filter(_.subcategories === subcategories).result.headOption
  )
}
