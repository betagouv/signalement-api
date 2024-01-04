package utils

import akka.Done
import akka.stream.IOResult
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.ObjectMetadata
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.amazonaws.HttpMethod
import services.S3ServiceInterface

import scala.concurrent.Future

class S3ServiceMock extends S3ServiceInterface {

  override def upload(bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]] = ???

  override def download(bucketKey: String): Future[ByteString] = ???

  override def downloadOnCurrentHost(bucketKey: String, filePath: String): Future[IOResult] = ???

  override def delete(bucketKey: String): Future[Done] = Future.successful(Done)

  override def getSignedUrl(bucketKey: String, method: HttpMethod): String = ???

  override def downloadFromBucket(bucketKey: String): Source[ByteString, Future[ObjectMetadata]] = ???
}
