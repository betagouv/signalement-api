package services

import models.report.extract.ZipElement
import models.report.extract.ZipElement.ZipReport
import models.report.extract.ZipElement.ZipReportFile
import orchestrators.reportexport.ZipEntryName
import org.apache.pekko.Done
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.s3.ObjectMetadata
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.util.ByteString
import play.api.Logger
import play.twirl.api.Html
import utils.Logs.RichLogger

import java.io.BufferedOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.ZipOutputStream
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

object ZipBuilder {

  val logger: Logger = Logger(this.getClass)

  def buildZip(
      zipEntries: Seq[(ZipEntryName, ZipElement)],
      getPdf: Seq[Html] => Source[ByteString, Unit],
      getAttachment: String => Source[ByteString, Future[ObjectMetadata]],
      existAttachment: String => Future[Boolean]
  )(implicit mat: Materializer, ec: ExecutionContext): Source[ByteString, Future[Done]] = {

    val pipedOut = new PipedOutputStream()
    val pipedIn  = new PipedInputStream(pipedOut)
    val zipOut   = new ZipOutputStream(new BufferedOutputStream(pipedOut))

    val zipFuture = Future {
      try {
        zipEntries.foreach { case (entryName, zipElement) =>
          val writeResult: Future[Done] = zipElement match {
            case ZipReport(html) =>
              zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName.value))
              getPdf(Seq(html)).runForeach { bytes =>
                zipOut.write(bytes.toArray)
              }
            case ZipReportFile(file) =>
              existAttachment(file.storageFilename)
                .flatMap {
                  case true =>
                    zipOut.putNextEntry(new java.util.zip.ZipEntry(entryName.value))
                    getAttachment(file.storageFilename).mapMaterializedValue(_ => ()).runForeach { bytes =>
                      zipOut.write(bytes.toArray)
                    }
                  case false =>
                    Future.successful(Done)
                }

          }

          val _ = Await.result(writeResult, Duration.Inf)
          zipOut.closeEntry()
        }
        zipOut.finish()
      } catch {
        case NonFatal(e) =>
          logger.errorWithTitle("zip_builder", "Error while writing zip entries", e)
          throw e
      } finally {
        try zipOut.close()
        catch { case _: Throwable => }
        try pipedOut.close()
        catch { case _: Throwable => }
      }
    }

    zipFuture.onComplete {
      case Success(_) =>
        logger.info("[zip_builder] ZIP creation completed successfully")
      case Failure(ex) =>
        logger.errorWithTitle("zip_builder", "ZIP creation failed", ex)
    }

    val zipSource: Source[ByteString, Future[IOResult]] =
      StreamConverters.fromInputStream(() => pipedIn)

    zipSource.watchTermination() { (_, ioResultFuture) =>
      ioResultFuture.onComplete {
        case Success(_) =>
          logger.info("[zip_builder] Stream completed successfully")
        case Failure(ex) =>
          logger.errorWithTitle("zip_builder", "Stream failed", ex)
      }
      ioResultFuture
    }
  }

}
