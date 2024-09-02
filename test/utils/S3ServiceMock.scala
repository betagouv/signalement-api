package utils

import org.apache.pekko.Done
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.amazonaws.HttpMethod
import org.apache.pekko.stream.connectors.s3.MultipartUploadResult
import org.apache.pekko.stream.connectors.s3.ObjectMetadata
import services.S3ServiceInterface

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Future

class S3ServiceMock(atomicQueue: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]())
    extends S3ServiceInterface {

  override def upload(bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]] = ???

  override def download(bucketKey: String): Future[ByteString] = ???

  override def downloadOnCurrentHost(bucketKey: String, filePath: String): Future[IOResult] = ???

  override def delete(bucketKey: String): Future[Done] = Future.successful {
    atomicQueue.remove(bucketKey)
    Done
  }

  override def getSignedUrl(bucketKey: String, method: HttpMethod): String = ???

  override def downloadFromBucket(bucketKey: String): Source[ByteString, Future[ObjectMetadata]] = ???

  override def exists(bucketKey: String): Future[Boolean] = ???
}
