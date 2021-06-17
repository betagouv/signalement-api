package controllers

import play.api.Configuration
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import repositories._
import models._
import play.api.libs.json._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv
import services.S3Service

@Singleton
class AsyncFileController @Inject() (
    val configuration: Configuration,
    val asyncFileRepository: AsyncFileRepository,
    val silhouette: Silhouette[AuthEnv],
    val s3Service: S3Service
)(implicit ec: ExecutionContext)
    extends BaseController {
  val BucketName = configuration.get[String]("play.buckets.report")

  def listAsyncFiles = SecuredAction.async { implicit request =>
    for {
      asyncFiles <- asyncFileRepository.list(request.identity)
    } yield Ok(Json.toJson(asyncFiles.map { case asyncFile: AsyncFile =>
      Map(
        "creationDate" -> asyncFile.creationDate.toString,
        "filename" -> asyncFile.filename.getOrElse(""),
        "url" -> asyncFile.storageFilename.map(s3Service.getSignedUrl(BucketName, _)).getOrElse("")
      )
    }))
  }
}
