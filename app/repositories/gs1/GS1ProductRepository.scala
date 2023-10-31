package repositories.gs1

import models.gs1.GS1Product
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import repositories.PostgresProfile.api._

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class GS1ProductRepository(dbConfig: DatabaseConfig[JdbcProfile])(implicit ec: ExecutionContext)
    extends GS1ProductRepositoryInterface {

  import dbConfig._

  val productTable    = GS1ProductTable.table
  val netContentTable = GS1NetContentTable.table

  override def create(product: GS1Product): Future[GS1Product] = db
    .run(
      (for {
        _ <- productTable += GS1ProductRow.fromDomain(product)
        _ <- DBIO.seq(GS1NetContentRow.fromDomain(product).getOrElse(List.empty).map(row => netContentTable += row): _*)
      } yield product).transactionally
    )

  override def getByGTIN(gtin: String): Future[Option[GS1Product]] = db
    .run(
      productTable
        .filter(_.gtin === gtin)
        .joinLeft(netContentTable)
        .on(_.id === _.gs1ProductId)
        .to[List]
        .result
    )
    .map(
      _.groupMap(_._1)(_._2)
        .map { case (gs1ProductRow, seq) =>
          val test = seq.flatten
          GS1ProductRow.toDomain(gs1ProductRow, if (test.isEmpty) None else Some(test))
        }
        .headOption
    )

  override def get(id: UUID): Future[Option[GS1Product]] = db
    .run(
      productTable
        .filter(_.id === id)
        .joinLeft(netContentTable)
        .on(_.id === _.gs1ProductId)
        .to[List]
        .result
    )
    .map(
      _.groupMap(_._1)(_._2)
        .map { case (gs1ProductRow, seq) =>
          val test = seq.flatten
          GS1ProductRow.toDomain(gs1ProductRow, if (test.isEmpty) None else Some(test))
        }
        .headOption
    )
}
