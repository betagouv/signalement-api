package services

import orchestrators.reportexport.ZipEntryName
import org.apache.pekko.stream.{IOResult, Materializer, OverflowStrategy}
import org.apache.pekko.stream.scaladsl.{Source, SourceQueueWithComplete}
import org.apache.pekko.util.ByteString
import play.api.Logger

import java.io.{BufferedOutputStream, OutputStream}
import java.util.zip.ZipOutputStream
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

object ZipBuilder {

  val logger: Logger = Logger(this.getClass)

  class QueueOutputStream(queue: SourceQueueWithComplete[ByteString])(implicit ec: ExecutionContext) extends OutputStream {
    private val buffer = new Array[Byte](8192)  // 8KB buffer

    override def write(b: Int): Unit = {
      buffer(0) = b.toByte
      write(buffer, 0, 1)
    }

    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      val bs = ByteString.fromArray(b, off, len)
      val offered = queue.offer(bs)
      offered.failed.foreach { ex =>
        // client probably disconnected
        queue.fail(ex)
      }
    }

    override def close(): Unit = {
      queue.complete() // signal end of stream
    }
  }

  def buildZip(
                        zipEntries: Seq[(ZipEntryName, Source[ByteString,  Unit])]
                      )(implicit mat: Materializer, ec: ExecutionContext): Source[ByteString,  Future[IOResult]] = {


    val queueSize = 16 // Number of chunks buffered in memory
    Source.queue[ByteString](queueSize)

    val (queue, source) =//zipSource.preMaterialize()
    Source.queue[ByteString](bufferSize = 16, OverflowStrategy.backpressure).preMaterialize()

    // Streaming writer
    Future {
      val zipOut = new ZipOutputStream(new BufferedOutputStream(new QueueOutputStream(queue)))
      try {
        zipEntries.foreach { case (entryName, dataSource) =>
          zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName.value))
          val writeFut = dataSource.runForeach { chunk =>
            zipOut.write(chunk.toArray)
          }
          Await.result(writeFut, Duration.Inf)
          zipOut.closeEntry()
        }
        zipOut.finish()
      } catch {
        case NonFatal(e) =>
          queue.fail(e)
      } finally {
        zipOut.close()
      }
    }

    source.mapMaterializedValue(_ => Future.successful(IOResult(1)))
  }



//  def buildZip(
//                zipEntries: Seq[(ZipEntryName, Source[ByteString, Unit])]
//              )(implicit ec: ExecutionContext, mat: Materializer): Source[ByteString, Future[IOResult]] = {
//
//    val pipedOut = new PipedOutputStream()
//    val pipedIn = new PipedInputStream(pipedOut)
//    val zipOut = new ZipOutputStream(new BufferedOutputStream(pipedOut))
//
//    // Start background ZIP writing
//    Future {
//      try {
//        zipEntries.foreach { case (entryName, entrySource) =>
//          zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName.value))
//
//          // Blockingly write each ByteString chunk
//          val writeResult = entrySource.runForeach { bytes =>
//            zipOut.write(bytes.toArray)
//          }
//
//          Await.result(writeResult, Duration.Inf)
//          zipOut.closeEntry()
//        }
//        zipOut.finish()
//      } catch {
//        case NonFatal(e) =>
//          logger.errorWithTitle("zip_builder", "Error while writing zip entries", e)
//          throw e
//      } finally {
//        try zipOut.close() catch { case _: Throwable => }
//        try pipedOut.close() catch { case _: Throwable => }
//      }
//    }
//
//    // Stream data from the piped input
//    val zipSource: Source[ByteString, Future[IOResult]] =
//      StreamConverters.fromInputStream(() => pipedIn)
//
//    zipSource.watchTermination() { (_, ioResultFuture) =>
//      ioResultFuture.onComplete {
//        case Success(_) =>
//          logger.info("[zip_builder] Stream completed successfully")
//        case Failure(ex) =>
//          logger.errorWithTitle("zip_builder", "Stream failed", ex)
//      }
//      ioResultFuture.map(_ => IOResult(1))
//    }
//  }

  

  

  

}
