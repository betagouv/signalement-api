package services

import orchestrators.reportexport.ZipEntryName
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.util.ByteString
import play.api.Logger
import utils.Logs.RichLogger

import java.io.BufferedOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipOutputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

object ZipBuilder {

  val logger: Logger = Logger(this.getClass)

  def buildZip(
      zipEntries: Seq[(ZipEntryName, Source[ByteString, Unit])]
  )(implicit executionContext: ExecutionContext, materializer: Materializer): Source[ByteString, Future[IOResult]] = {

    val (zipOut, pipedOut, source) = createZipStreams()

    val fileSourcesFutures = zipEntries.map { case (fileName, zipEntrySource) =>
      println(s"------------------ fileName = ${fileName} ------------------")
      writeZipEntries(zipEntrySource, fileName, zipOut)
    }

    // Make sure to wait all operations before closing the zip
    Future
      .sequence(fileSourcesFutures)
      .andThen {
        case Success(_) =>
          safelyCloseResources(zipOut, pipedOut)
        case Failure(e) =>
          logger.errorWithTitle("zip_builder", "Error in processing files", e)
          safelyCloseResources(zipOut, pipedOut)
      }: Unit

    source
  }

  private def writeZipEntries(
      input: Source[ByteString, Unit],
      entryName: ZipEntryName,
      zipOut: ZipOutputStream
  )(implicit executionContext: ExecutionContext, materializer: Materializer): Future[Unit] =
    input.runWith(Sink.fold(ByteString.empty)(_ ++ _)).map { byteString =>
      zipOut.synchronized {
        zipOut.putNextEntry(new java.util.zip.ZipEntry(s"${entryName.value}"))
        zipOut.write(byteString.toArray)
        zipOut.closeEntry()
      }
    }

  private def createZipStreams(): (ZipOutputStream, PipedOutputStream, Source[ByteString, Future[IOResult]]) = {
    val pipedOut = new PipedOutputStream()
    val zipOut   = new ZipOutputStream(new BufferedOutputStream(pipedOut))
    val source   = StreamConverters.fromInputStream(() => new PipedInputStream(pipedOut))
    (zipOut, pipedOut, source)
  }

  private def safelyCloseResources(zipOut: ZipOutputStream, pipedOut: PipedOutputStream): Unit = {
    zipOut.synchronized {
      try {
        zipOut.finish() // Ensure all data is finalized in the ZIP file
        zipOut.close()  // Then close the ZIP output stream
      } catch {
        case NonFatal(ex) =>
          logger.errorWithTitle("zip_builder", "Error finalizing or closing zipOut", ex)
      }
    }
    try
      pipedOut.close() // Close the PipedOutputStream
    catch {
      case NonFatal(ex) =>
        logger.errorWithTitle("zip_builder", "Error closing pipedOut", ex)
    }
  }

}
