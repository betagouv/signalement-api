package services

import controllers.error.AppError.ServerError
import orchestrators.reportexport.ZipEntryName
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete
import org.apache.pekko.util.ByteString
import play.api.Logger

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.zip.ZipOutputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.util.Failure
import scala.util.Success

object ZipBuilder {

  val logger: Logger = Logger(this.getClass)

  private class QueueOutputStream(queue: SourceQueueWithComplete[ByteString])(implicit ec: ExecutionContext)
      extends OutputStream {

    private var lastOffer: Future[QueueOfferResult] = Future.successful(QueueOfferResult.Enqueued)

    override def write(b: Int): Unit =
      write(Array(b.toByte), 0, 1)

    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      val bs = ByteString.fromArray(b, off, len)
      lastOffer = lastOffer.flatMap { _ =>
        val offerFuture = queue.offer(bs)

        offerFuture.failed.foreach { ex =>
          logger.error("Error while offering to the queue", ex)
          queue.fail(ex)
        }

        offerFuture.flatMap {
          case QueueOfferResult.Enqueued => Future.successful(QueueOfferResult.Enqueued)
          case other =>
            val ex = ServerError(s"Queue offer failed: $other")
            logger.error("Offer rejected by the queue: " + other)
            queue.fail(ex)
            Future.failed(ex)
        }
      }
    }

    override def close(): Unit = () // La queue est close ailleurs, ne pas supprimer
  }

  def buildZip(
      zipEntries: Seq[(ZipEntryName, Source[ByteString, _])]
  )(implicit mat: Materializer, ec: ExecutionContext): Source[ByteString, Future[Done]] = {

    val (queue, source) =
      Source
        .queue[ByteString](bufferSize = 16, OverflowStrategy.backpressure)
        .preMaterialize()

    val writing: Future[Unit] = Future {
      new ZipOutputStream(new BufferedOutputStream(new QueueOutputStream(queue)))
    }.flatMap { zipOut =>
      val entriesWritten = zipEntries.foldLeft(Future.successful(())) { case (prevFut, (entryName, dataSource)) =>
        prevFut.flatMap { _ =>
          zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName.value))
          dataSource
            .runForeach { chunk =>
              blocking {
                zipOut.write(chunk.toArray)
              }
            }
            .map(_ => zipOut.closeEntry())
        }
      }

      entriesWritten.flatMap(_ => Future(zipOut.finish())).transformWith {
        case Success(_) =>
          Future(zipOut.close()).map(_ => ())
        case Failure(ex) =>
          logger.error("Error while closing the ZipOutputStream", ex)
          Future(zipOut.close()).flatMap(_ => Future.failed(ex))
      }
    }.recoverWith { case ex =>
      logger.error("Error during ZIP file creation", ex)
      queue.fail(ex)
      Future.failed(ex)
    }.map { _ =>
      queue.complete()
    }

    source.watchTermination() { (_, done) =>
      writing.flatMap(_ => done)
    }
  }
}
