import models.PaginatedResult
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

package object repositories {

  implicit class PaginateOps[A, B](query: slick.lifted.Query[A, B, Seq])(implicit executionContext: ExecutionContext){

    def toPaginate(db:JdbcBackend#DatabaseDef)(maybeOffset: Option[Long], maybeLimit: Option[Int]): Future[PaginatedResult[B]] = {

      val offset: Long = maybeOffset.getOrElse(0L)
      val queryWithLimit: Query[A, B, Seq] = maybeLimit.map(query.take).getOrElse(query)

      val resultF: Future[Seq[B]] = db.run(queryWithLimit.drop(offset).result)
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
