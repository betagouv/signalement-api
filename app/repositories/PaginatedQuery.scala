package repositories

import models.PaginatedResult
import slick.jdbc.JdbcBackend
import slick.lifted.Ordered
import repositories.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

case class PaginatedQuery[A, B](
                                 db: JdbcBackend#Database,
                                 query: slick.lifted.Query[A, B, Seq],
                                 maybeOffset: Option[Long],
                                 maybeLimit: Option[Int],
                                 maybePreliminaryAction: Option[DBIO[Int]]
                               ) {
  def unsorted(implicit ec: ExecutionContext): Future[PaginatedResult[B]] = {

    val offset = maybeOffset.map(Math.max(_, 0)).getOrElse(0L)
    val limit  = maybeLimit.map(Math.max(_, 0))

    val queryWithOffset         = query.drop(offset)
    val queryWithOffsetAndLimit = limit.map(l => queryWithOffset.take(l)).getOrElse(queryWithOffset)

    val resultF: Future[Seq[B]] = db.run(
      maybePreliminaryAction match {
        case Some(action) =>
          (for {
            _      <- action
            result <- queryWithOffsetAndLimit.result
          } yield result).transactionally

        case None => queryWithOffsetAndLimit.result
      }
    )

    val countF: Future[Int] = db.run(
      maybePreliminaryAction match {
        case Some(action) =>
          (for {
            _      <- action
            result <- query.length.result
          } yield result).transactionally

        case None => query.length.result

      }
    )

    for {
      result <- resultF
      count  <- countF
    } yield PaginatedResult(
      totalCount = count,
      entities = result.toList,
      hasNextPage = limit.exists(l => count - (offset + l) > 0)
    )
  }

  def sortBy[T](f: A => T)(implicit ev: T => Ordered, ec: ExecutionContext): Future[PaginatedResult[B]] = {
    val sortedQuery = query.sortBy(f)

    val offset = maybeOffset.map(Math.max(_, 0)).getOrElse(0L)
    val limit  = maybeLimit.map(Math.max(_, 0))

    val queryWithOffset         = sortedQuery.drop(offset)
    val queryWithOffsetAndLimit = limit.map(l => queryWithOffset.take(l)).getOrElse(queryWithOffset)

    val resultF: Future[Seq[B]] = db.run(
      maybePreliminaryAction match {
        case Some(action) =>
          (for {
            _      <- action
            result <- queryWithOffsetAndLimit.result
          } yield result).transactionally

        case None => queryWithOffsetAndLimit.result
      }
    )

    val countF: Future[Int] = db.run(
      maybePreliminaryAction match {
        case Some(action) =>
          (for {
            _      <- action
            result <- query.length.result
          } yield result).transactionally

        case None => query.length.result

      }
    )

    for {
      result <- resultF
      count  <- countF
    } yield PaginatedResult(
      totalCount = count,
      entities = result.toList,
      hasNextPage = limit.exists(l => count - (offset + l) > 0)
    )
  }
}
