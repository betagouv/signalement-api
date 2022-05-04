package repositories

import repositories.PostgresProfile.api._
import slick.lifted.Rep
import slick.lifted.Tag

import java.util.UUID

abstract class DatabaseTable[T](tag: Tag, name: String) extends Table[T](tag, name) {
  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
}
