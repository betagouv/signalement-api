package services

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.scaladsl.S3Client
import javax.inject.{Inject, Singleton}

@Singleton
class S3Service @Inject()(system: ActorSystem, materializer: Materializer) {

  private val s3Client = S3Client.apply()(system, materializer)

  def upload(bucketName: String, bucketKey: String) =
    s3Client.multipartUpload(bucketName, bucketKey)

}