package repositories

import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait CRUDRepositoryInterface[E] {

  def create(element: E): Future[E]

  def update(id: UUID, element: E): Future[E]

  def get(id: UUID): Future[Option[E]]

  def delete(id: UUID): Future[Int]

  def list(): Future[List[E]]

}

abstract class CRUDRepository[T <: DatabaseTable[E], E] extends CRUDRepositoryInterface[E] {

  val table: TableQuery[T]
  implicit val ec: ExecutionContext
  val dbConfig: DatabaseConfig[JdbcProfile]

  import dbConfig._

  def create(element: E): Future[E] = db
    .run(
      table returning table += element
    )
    .map(_ => element)

  def update(id: UUID, element: E): Future[E] =
    db.run(
      table.filter(_.id === id).update(element)
    ).map(_ => element)

  def get(id: UUID): Future[Option[E]] = db.run(
    table.filter(_.id === id).result.headOption
  )

  def delete(id: UUID): Future[Int] = db.run(
    table.filter(_.id === id).delete
  )

  def list(): Future[List[E]] = db.run(table.to[List].result)

}
