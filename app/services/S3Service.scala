package services

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

@Singleton
class S3Service @Inject()(implicit val system: ActorSystem, val materializer: Materializer, val executionContext: ExecutionContext) {

  private val s3Client = S3

  def upload(bucketName: String, bucketKey: String) =
    s3Client.multipartUpload(bucketName, bucketKey)

  def download(bucketName: String, bucketKey: String) =
    s3Client.download(bucketName, bucketKey).runWith(Sink.head).map(_.map(_._1))
      .flatMap(a => a.get.runWith(Sink.reduce((a: ByteString, b: ByteString) => a ++ b)))


  def delete(bucketName: String, bucketKey: String)=
    s3Client.deleteObject(bucketName, bucketKey).runWith(Sink.head)
}