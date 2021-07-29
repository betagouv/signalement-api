import models.PaginatedResult
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcBackend

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

package object repositories {

  implicit class PaginateOps[A, B](query: slick.lifted.Query[A, B, Seq])(implicit executionContext: ExecutionContext) {

    def withPagination(
        db: JdbcBackend#DatabaseDef
    )(maybeOffset: Option[Long], maybeLimit: Option[Int]): Future[PaginatedResult[B]] = {

      val offset: Long = maybeOffset.getOrElse(0L)
      val queryWithOffset = query.drop(offset)
      val queryWithOffsetAndLimit: Query[A, B, Seq] = maybeLimit.map(queryWithOffset.take).getOrElse(queryWithOffset)

      val resultF: Future[Seq[B]] = db.run(queryWithOffsetAndLimit.result)
      val countF: Future[Int] = db.run(query.length.result)
      for {
        result <- resultF
        count <- countF
      } yield PaginatedResult(
        totalCount = count,
        entities = result.toList,
        hasNextPage = maybeLimit.exists(limit => count - (offset + limit) > 0)
      )
    }

  }

}
