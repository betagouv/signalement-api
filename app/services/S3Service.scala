package services

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.amazonaws.HttpMethod
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import play.api.Configuration
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class S3Service @Inject() (implicit
    val system: ActorSystem,
    val materializer: Materializer,
    val executionContext: ExecutionContext,
    val configuration: Configuration
) {

  private val alpakkaS3Client = S3
  private val awsS3Client = AmazonS3ClientBuilder
    .standard()
    .withEndpointConfiguration(
      new EndpointConfiguration("https://cellar-c2.services.clever-cloud.com", "us-east-1")
    )
    .withCredentials(
      new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(
          configuration.get[String]("alpakka.s3.aws.credentials.access-key-id"),
          configuration.get[String]("alpakka.s3.aws.credentials.secret-access-key")
        )
      )
    )
    .build()

  def upload(bucketName: String, bucketKey: String) =
    alpakkaS3Client.multipartUpload(bucketName, bucketKey)

  def download(bucketName: String, bucketKey: String) =
    alpakkaS3Client
      .download(bucketName, bucketKey)
      .runWith(Sink.head)
      .map(_.map(_._1))
      .flatMap(a => a.get.runWith(Sink.reduce((a: ByteString, b: ByteString) => a ++ b)))

  def delete(bucketName: String, bucketKey: String) =
    alpakkaS3Client.deleteObject(bucketName, bucketKey).runWith(Sink.head)

  def getSignedUrl(bucketName: String, bucketKey: String, method: HttpMethod = HttpMethod.GET): String = {
    // See https://docs.aws.amazon.com/AmazonS3/latest/dev/ShareObjectPreSignedURLJavaSDK.html
    val expiration = new java.util.Date
    expiration.setTime(expiration.getTime + 1000 * 60 * 60)
    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, bucketKey)
      .withMethod(method)
      .withExpiration(expiration)
    awsS3Client.generatePresignedUrl(generatePresignedUrlRequest).toString
  }
}
