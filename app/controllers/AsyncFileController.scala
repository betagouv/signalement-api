package controllers

import models._
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import repositories.asyncfiles.AsyncFileRepositoryInterface
import services.S3ServiceInterface
import utils.auth.Authenticator

import scala.concurrent.ExecutionContext

class AsyncFileController(
    asyncFileRepository: AsyncFileRepositoryInterface,
    s3Service: S3ServiceInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  def listAsyncFiles(kind: Option[String]) = SecuredAction.async { implicit request =>
    for {
      asyncFiles <- asyncFileRepository.list(request.identity, kind.map(AsyncFileKind.withName))
    } yield Ok(Json.toJson(asyncFiles.map { asyncFile: AsyncFile =>
      Map(
        "id"           -> asyncFile.id.toString,
        "creationDate" -> asyncFile.creationDate.toString,
        "filename"     -> asyncFile.filename.getOrElse(""),
        "kind"         -> asyncFile.kind.toString,
        "url"          -> asyncFile.storageFilename.map(s3Service.getSignedUrl(_)).getOrElse("")
      )
    }))
  }
}
