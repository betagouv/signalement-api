package controllers

import akka.NotUsed
import akka.stream.alpakka.file.ArchiveMetadata
import akka.stream.alpakka.file.scaladsl.Archive
import akka.stream.scaladsl.Source
import akka.util.ByteString
import authentication.Authenticator
import models.Consumer
import orchestrators.DataEconomieOrchestrator
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

  def reportDataEcomonie() = SecuredAction.async(parse.empty) { _ =>
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
