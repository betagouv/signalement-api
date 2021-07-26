import models.PaginatedResult
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

package object repositories {

  implicit class PaginateOps[A, B](value: slick.lifted.Query[A, B, Seq])(implicit executionContext: ExecutionContext){

    def toPaginate(db:JdbcBackend#DatabaseDef)(maybeOffset: Option[Long], maybeLimit: Option[Int]): Future[PaginatedResult[B]] = {

      val offset: Long = maybeOffset.getOrElse(0L)
      val limit: Int = maybeLimit.getOrElse(10)
      val resultF: Future[Seq[B]] = db.run(value.drop(offset).take(limit).result)
      val countF: Future[Int] = db.run(value.length.result)
      for {
        result <- resultF
        count <- countF
      } yield PaginatedResult(
        totalCount = count,
        entities = result.toList,
        hasNextPage = count - (offset + limit) > 0
      )
    }
  }

}
