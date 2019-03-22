package services

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.scaladsl.Sink
import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

@Singleton
class S3Service @Inject()(implicit val system: ActorSystem, val materializer: Materializer, val executionContext: ExecutionContext) {

  private val s3Client = S3Client.apply()

  def upload(bucketName: String, bucketKey: String) =
    s3Client.multipartUpload(bucketName, bucketKey)

  def download(bucketName: String, bucketKey: String) =
    s3Client.download(bucketName, bucketKey)._1.runWith(Sink.head).map(_.asByteBuffer)

  def delete(bucketName: String, bucketKey: String)=
    s3Client.deleteObject(bucketName, bucketKey)

}