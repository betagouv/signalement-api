package services

import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.nio.file.Path
import com.amazonaws.HttpMethod
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import config.BucketConfiguration
import controllers.error.AppError.BucketFileNotFound

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class S3Service @Inject() (implicit
    val materializer: Materializer,
    val executionContext: ExecutionContext,
    val bucketConfiguration: BucketConfiguration
) {
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

  def upload(bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]] =
    alpakkaS3Client.multipartUpload(bucketName, bucketKey)

  def download(bucketKey: String): Future[ByteString] =
    downloadFromBucket(bucketKey)
      .flatMap(a => a.runWith(Sink.reduce((a: ByteString, b: ByteString) => a ++ b)))

  def downloadOnCurrentHost(bucketKey: String, filename: String): Future[IOResult] =
    downloadFromBucket(bucketKey).flatMap(a => a.runWith(FileIO.toPath(Path.of(s"./${filename}"))))

  private def downloadFromBucket(bucketKey: String) =
    alpakkaS3Client
      .download(bucketName, bucketKey)
      .runWith(Sink.head)
      .map {
        case Some((byteStringSource, _)) => byteStringSource
        case None                        => throw BucketFileNotFound(bucketName, bucketKey)
      }

  def delete(bucketKey: String) =
    alpakkaS3Client.deleteObject(bucketName, bucketKey).runWith(Sink.head)

  def getSignedUrl(bucketKey: String, method: HttpMethod = HttpMethod.GET): String = {
    // See https://docs.aws.amazon.com/AmazonS3/latest/dev/ShareObjectPreSignedURLJavaSDK.html
    val expiration = new java.util.Date
    expiration.setTime(expiration.getTime + 1000 * 60 * 60)
    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, bucketKey)
      .withMethod(method)
      .withExpiration(expiration)
    awsS3Client.generatePresignedUrl(generatePresignedUrlRequest).toString
  }
}
