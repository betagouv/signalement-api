package services

import akka.Done
import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import utils.Logs.RichLogger
import java.nio.file.Path
import com.amazonaws.HttpMethod
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ResponseHeaderOverrides
import config.BucketConfiguration
import play.api.Logger

import java.io.BufferedOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.UUID
import java.util.zip.ZipOutputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

class S3Service(implicit
    val materializer: Materializer,
    val executionContext: ExecutionContext,
    val bucketConfiguration: BucketConfiguration
) extends S3ServiceInterface {
  val logger: Logger           = Logger(this.getClass)
  private[this] val bucketName = bucketConfiguration.amazonBucketName

  private val alpakkaS3Client = S3
  private val awsS3Client = AmazonS3ClientBuilder
    .standard()
    .withEndpointConfiguration(
      new EndpointConfiguration("https://cellar-c2.services.clever-cloud.com", "us-east-1")
    )
    .withCredentials(
      new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(
          bucketConfiguration.keyId,
          bucketConfiguration.secretKey
        )
      )
    )
    .build()

  override def upload(bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]] =
    alpakkaS3Client.multipartUpload(bucketName, bucketKey)

  override def download(bucketKey: String): Future[ByteString] =
    downloadFromBucket(bucketKey).runWith(Sink.reduce((a: ByteString, b: ByteString) => a ++ b))

  override def downloadOnCurrentHost(bucketKey: String, filePath: String): Future[IOResult] =
    downloadFromBucket(bucketKey).runWith(FileIO.toPath(Path.of(filePath)))

  private def downloadFromBucket(bucketKey: String) =
    alpakkaS3Client
      .getObject(bucketName, bucketKey)

  override def delete(bucketKey: String): Future[Done] =
    alpakkaS3Client.deleteObject(bucketName, bucketKey).runWith(Sink.head)

  override def downloadAndZip(files: Seq[String]): Source[ByteString, Future[IOResult]] = {
    val pipedOut = new PipedOutputStream()
    val zipOut   = new ZipOutputStream(new BufferedOutputStream(pipedOut))
    val source   = StreamConverters.fromInputStream(() => new PipedInputStream(pipedOut))

    val fileSourcesFutures: Seq[Future[Unit]] = files.map { fileName =>
      S3.getObject(bucketName, fileName).runWith(Sink.fold(ByteString.empty)(_ ++ _)).map { byteString =>
        zipOut.synchronized {
          zipOut.putNextEntry(new java.util.zip.ZipEntry(UUID.randomUUID().toString ++ fileName))
          zipOut.write(byteString.toArray)
          zipOut.closeEntry()
        }
      } recover { case NonFatal(e) =>
        logger.errorWithTitle("mass_report_files_download", "Error while downloading the files", e)
      }
    }

    // Make sure all files are downloaded before closing the zip
    Future
      .sequence(fileSourcesFutures)
      .andThen { case _ =>
        try
          zipOut.synchronized {
            zipOut.finish()
            zipOut.close()
          }
        catch {
          case NonFatal(e) =>
            logger.errorWithTitle("mass_report_files_download", "Error closing zip file", e)
        } finally
          pipedOut.close()
        ()
      }

    source
  }

  override def getSignedUrl(bucketKey: String, method: HttpMethod = HttpMethod.GET): String = {
    val headerOverrides = new ResponseHeaderOverrides()
    // Force attachment to be download by browser
    headerOverrides.setContentDisposition("attachment;")
    // See https://docs.aws.amazon.com/AmazonS3/latest/dev/ShareObjectPreSignedURLJavaSDK.html
    val expiration = new java.util.Date
    expiration.setTime(expiration.getTime + 1000 * 60 * 60)
    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, bucketKey)
      .withMethod(method)
      .withResponseHeaders(headerOverrides)
      .withExpiration(expiration)
    awsS3Client.generatePresignedUrl(generatePresignedUrlRequest).toString
  }
}
