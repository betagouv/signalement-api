package services
import akka.Done
import akka.stream.IOResult
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.amazonaws.HttpMethod
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[S3Service])
trait S3ServiceInterface {

  def upload(bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]]

  def download(bucketKey: String): Future[ByteString]

  def downloadOnCurrentHost(bucketKey: String, filename: String): Future[IOResult]

  def delete(bucketKey: String): Future[Done]

  def getSignedUrl(bucketKey: String, method: HttpMethod = HttpMethod.GET): String
}
