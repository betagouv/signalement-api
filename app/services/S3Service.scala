package services

import org.apache.pekko.Done
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import java.nio.file.Path
import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ResponseHeaderOverrides
import config.BucketConfiguration
import org.apache.pekko.stream.connectors.s3.MultipartUploadResult
import org.apache.pekko.stream.connectors.s3.ObjectMetadata
import org.apache.pekko.stream.connectors.s3.scaladsl.S3
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class S3Service(awsS3Client: AmazonS3)(implicit
    val materializer: Materializer,
    val executionContext: ExecutionContext,
    val bucketConfiguration: BucketConfiguration
) extends S3ServiceInterface {
  val logger: Logger           = Logger(this.getClass)
  private[this] val bucketName = bucketConfiguration.amazonBucketName

  private val pekkoS3Client = S3

  override def upload(bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]] =
    pekkoS3Client.multipartUpload(bucketName, bucketKey)

  override def uploadZipSource(zipSource: Source[ByteString, Future[Done]], bucketKey: String): Future[Done] =
    S3
      .multipartUpload(bucketName, bucketKey)
      .runWith(zipSource)

  override def download(bucketKey: String): Future[ByteString] =
    downloadFromBucket(bucketKey).runWith(Sink.reduce((a: ByteString, b: ByteString) => a ++ b))

  override def downloadOnCurrentHost(bucketKey: String, filePath: String): Future[IOResult] =
    downloadFromBucket(bucketKey).runWith(FileIO.toPath(Path.of(filePath)))

  def downloadFromBucket(bucketKey: String): Source[ByteString, Future[ObjectMetadata]] =
    pekkoS3Client
      .getObject(bucketName, bucketKey)

  def exists(bucketKey: String): Future[Boolean] =
    S3.getObjectMetadata(bucketName, bucketKey).runWith(Sink.headOption).map { b =>
      b.flatten.isDefined
    }

  override def delete(bucketKey: String): Future[Done] =
    pekkoS3Client.deleteObject(bucketName, bucketKey).runWith(Sink.head)

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
