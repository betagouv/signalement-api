package controllers

import akka.NotUsed
import akka.stream.alpakka.file.ArchiveMetadata
import akka.stream.alpakka.file.scaladsl.Archive
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppErrorTransformer.handleError
import orchestrators.DataEconomieOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DataEconomieController @Inject() (
    service: DataEconomieOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    val silhouetteAPIKey: Silhouette[APIKeyEnv]
)(implicit val executionContext: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def reportDataEcomonie() = silhouetteAPIKey.SecuredAction.async(parse.empty) { request =>
    val source: Source[ByteString, Any] =
      service
        .getReportDataEconomie()
        .map(Json.toJson(_).toString())
        .intersperse[String]("[", ",", "]")
        .map(x => (ByteString(x.getBytes)))

    val zipSource: Source[ByteString, NotUsed] =
      Source(List((ArchiveMetadata(s"${DataEconomieController.ReportFileName}.json"), source)))
        .via(Archive.zip())

    Future
      .successful(
        Ok.chunked(zipSource)
          .withHeaders(("Content-Disposition", s"attachment; filename=${DataEconomieController.ReportFileName}.zip"))
      )
      .recover { case err => handleError(err, Some(request.identity.id)) }
  }
}

object DataEconomieController {

  def ReportFileName = s"signalements"

}
