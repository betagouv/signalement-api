package controllers

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import authentication.Authenticator
import models.Consumer
import orchestrators.DataEconomieOrchestrator
import org.apache.pekko.stream.connectors.file.ArchiveMetadata
import org.apache.pekko.stream.connectors.file.scaladsl.Archive
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DataEconomieController(
    service: DataEconomieOrchestrator,
    authenticator: Authenticator[Consumer],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends ApiKeyBaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def reportDataEcomonie() = Act.securedbyApiKey.async(parse.empty) { _ =>
    val source: Source[ByteString, Any] =
      service
        .getReportDataEconomie()
        .map(Json.toJson(_).toString())
        .intersperse[String]("[", ",", "]")
        .map(x => ByteString(x.getBytes))

    val zipSource: Source[ByteString, NotUsed] =
      Source(List((ArchiveMetadata(s"${DataEconomieController.ReportFileName}.json"), source)))
        .via(Archive.zip())

    Future
      .successful(
        Ok.chunked(zipSource)
          .withHeaders(("Content-Disposition", s"attachment; filename=${DataEconomieController.ReportFileName}.zip"))
      )
  }
}

object DataEconomieController {

  def ReportFileName = s"signalements"

}
