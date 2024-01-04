package services

import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
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
  case class ReportZipEntryName(value: String)

  def buildZip(
      zipEntries: Seq[(ReportZipEntryName, Source[ByteString, Unit])]
  )(implicit executionContext: ExecutionContext, materializer: Materializer): Source[ByteString, Future[IOResult]] = {

    val (zipOut, pipedOut, source) = createZipStreams()

    val fileSourcesFutures = zipEntries.map { case (fileName, zipEntrySource) =>
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
      entryName: ReportZipEntryName,
      zipOut: ZipOutputStream
  )(implicit executionContext: ExecutionContext, materializer: Materializer): Future[Unit] =
    input.runWith(Sink.fold(ByteString.empty)(_ ++ _)).map { byteString =>
      zipOut.synchronized {
        zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName.value))
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
