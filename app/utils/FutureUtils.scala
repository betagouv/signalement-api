package utils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object FutureUtils {

  implicit class RichSeq[A](seq: Seq[A]) {

    // Helper to iterate over a collection, do some async operation on each item of the collection
    // but in sequence. Not in parallel.
    // Usage :
    //    myCollectionOfThings.runSequentially(thing => somethingThatReturnsAFuture(thing))
    //
    def runSequentially[B](func: A => Future[B])(implicit ec: ExecutionContext): Future[List[B]] =
      seq.foldLeft(Future.successful(List[B]())) { (previous, item) =>
        for {
          cumulatedResults <- previous
          newResult        <- func(item)
        } yield cumulatedResults :+ newResult
      }
  }

}
