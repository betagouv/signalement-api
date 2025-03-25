package models

import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Writes

case class PaginatedResult[T](
    totalCount: Int,
    hasNextPage: Boolean,
    entities: List[T]
) {
  def mapEntities[A](fn: T => A): PaginatedResult[A] =
    this.copy(entities = entities.map(fn))
}

object PaginatedResult {
  implicit def paginatedResultWrites[T](implicit tWrites: Writes[T]): OWrites[PaginatedResult[T]] =
    Json.writes[PaginatedResult[T]]
}
