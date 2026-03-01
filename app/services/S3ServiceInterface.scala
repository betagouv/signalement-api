package services
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.Done
import org.apache.pekko.stream.connectors.s3.MultipartUploadResult
import org.apache.pekko.stream.connectors.s3.ObjectMetadata
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

trait S3ServiceInterface {

  def upload(bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]]

  def download(bucketKey: String): Future[ByteString]

  def delete(bucketKey: String): Future[Done]

  def getSignedUrl(bucketKey: String): String

  def downloadFromBucket(bucketKey: String): Source[ByteString, Future[ObjectMetadata]]

  def exists(bucketKey: String): Future[Boolean]

  def uploadZipSource(zipSource: Source[ByteString, Future[Done]], bucketKey: String): Future[Done]
}
