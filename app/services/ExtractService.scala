package services

import models.User
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import play.api.Logger
import repositories.asyncfiles.AsyncFileRepositoryInterface

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try

class ExtractService(asyncFileRepository: AsyncFileRepositoryInterface, s3Service: S3ServiceInterface)(implicit
    ec: ExecutionContext,
    mat: Materializer
) {

  def buildAndUploadFile(
      tmpPath: Path,
      fileId: UUID,
      requestedBy: User,
      folder: String
  ): Future[Unit] = {
    val upload = for {
      remotePath <- saveRemotely(folder)(s3Service, tmpPath, tmpPath.getFileName.toString)
      _          <- asyncFileRepository.update(fileId, tmpPath.getFileName.toString, remotePath)
    } yield logger.debug(s"Built reportedPhones for User ${requestedBy.id} — async file ${fileId}")

    upload.andThen { case _ => deleteTmpFile(tmpPath) }
  }

  val logger: Logger = Logger(this.getClass)

  private def saveRemotely(
      folder: String
  )(s3Service: S3ServiceInterface, localPath: Path, remoteName: String): Future[String] = {
    val remotePath = s"${folder}/${remoteName}"
    s3Service.upload(remotePath).runWith(FileIO.fromPath(localPath)).map(_ => remotePath)
  }

  def deleteTmpFile(path: Path)(implicit ec: ExecutionContext): Future[Unit] =
    Future {
      Try(Files.deleteIfExists(path)) match {
        case Failure(ex) => logger.error(s"Failed to delete tmp file $path", ex)
        case _           => ()
      }
    }

}
