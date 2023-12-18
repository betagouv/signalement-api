package controllers

<<<<<<< Updated upstream
import com.mohiva.play.silhouette.api.Silhouette
=======
import com.amazonaws.HttpMethod
import authentication.Authenticator
>>>>>>> Stashed changes
import models._
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import repositories.asyncfiles.AsyncFileRepositoryInterface
import services.S3ServiceInterface
import utils.silhouette.auth.AuthEnv

import scala.concurrent.ExecutionContext

class AsyncFileController(
    val asyncFileRepository: AsyncFileRepositoryInterface,
    val silhouette: Silhouette[AuthEnv],
    val s3Service: S3ServiceInterface,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  def listAsyncFiles(kind: Option[String], inline: Boolean = false) = SecuredAction.async { implicit request =>
    for {
      asyncFiles <- asyncFileRepository.list(request.identity, kind.map(AsyncFileKind.withName))
    } yield Ok(Json.toJson(asyncFiles.map { case asyncFile: AsyncFile =>
      Map(
        "id"           -> asyncFile.id.toString,
        "creationDate" -> asyncFile.creationDate.toString,
        "filename"     -> asyncFile.filename.getOrElse(""),
        "kind"         -> asyncFile.kind.toString,
        "url"          -> asyncFile.storageFilename.map(s3Service.getSignedUrl(_, HttpMethod.GET, inline)).getOrElse("")
      )
    }))
  }
}
