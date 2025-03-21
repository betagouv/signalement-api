
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcBackend

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
package object repositories {

  implicit class PaginateOps[A, B](query: slick.lifted.Query[A, B, Seq]) {

    def optimiseFullTextSearch(setOffset: Boolean): slick.lifted.Query[A, B, Seq] =
      if (setOffset) query.drop(0) else query

    def withPagination(
        db: JdbcBackend#Database
    )(
        maybeOffset: Option[Long],
        maybeLimit: Option[Int],
        maybePreliminaryAction: Option[DBIO[Int]] = None
    ): PaginatedQuery[A, B] =
      PaginatedQuery(
        db,
        query,
        maybeOffset,
        maybeLimit,
        maybePreliminaryAction
      )

  }

  def computeTickValues(ticks: Int) = Seq
    .iterate(
      OffsetDateTime.now().minusMonths(ticks.toLong - 1L).withDayOfMonth(1),
      ticks
    )(_.plusMonths(1))
    .map(_.toLocalDate)
    .map(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(_))
    .map(t => s"('$t'::timestamp)")
    .mkString(",")

}
