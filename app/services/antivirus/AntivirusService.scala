package services

import config.AntivirusServiceConfiguration
import models.report.reportfile.ReportFileId
import play.api.Logging
import services.AntivirusService.AntivirusScanRequestFailed
import services.AntivirusService.AntivirusServiceError
import services.AntivirusService.AntivirusServiceFileStatusError
import services.AntivirusService.AntivirusServiceUnexpectedError
import services.antivirus.FileData
import services.antivirus.ScanCommand
import sttp.capabilities
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.playJson.asJson
import sttp.client3.playJson.playJsonBodySerializer
import sttp.model.Header
import sttp.model.StatusCode
import utils.Logs.RichLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait AntivirusServiceInterface {
  def scan(reportFileId: ReportFileId, storageFileName: String): Future[Either[AntivirusServiceError, Unit]]
  def reScan(reportFileId: List[ReportFileId]): Future[Either[AntivirusServiceError, Unit]]
  def fileStatus(reportFileId: ReportFileId): Future[Either[AntivirusServiceError, FileData]]

  def isActive: Boolean
}

class AntivirusService(conf: AntivirusServiceConfiguration, backend: SttpBackend[Future, capabilities.WebSockets])(
    implicit executionContext: ExecutionContext
) extends AntivirusServiceInterface
    with Logging {

  val ScanEndpoint       = "/api/file/scan"
  val ReScanEndPoint     = "/api/file/rescan"
  val ScanStatusEndPoint = "/api/file"

  override def scan(
      reportFileId: ReportFileId,
      storageFileName: String
  ): Future[Either[AntivirusServiceError, Unit]] = {

    val request = basicRequest
      .headers(Header("X-Api-Key", conf.antivirusApiKey))
      .post(
        uri"${conf.antivirusApiUrl}".withWholePath(ScanEndpoint)
      )
      .body(ScanCommand(reportFileId.value.toString, storageFileName))

    val response =
      request.send(backend)

    response
      .map { res =>
        res.code match {
          case StatusCode.NoContent || StatusCode.Ok =>
            logger.debug("Scan request successful: No content returned as expected.")
            Right(())
          case _ =>
            logger.warnWithTitle(
              "antivirus_scan_request_error",
              s"Unexpected response status for scan reportId : ${reportFileId.value} : ${res.code}, body: ${res.body}"
            )
            Left(AntivirusScanRequestFailed)
        }
      }
      .recover { case error: Throwable =>
        logger.warnWithTitle(
          "antivirus_scan_request_error",
          s"Cannot send antivirus request for reportId : ${reportFileId.value}",
          error
        )
        Left(AntivirusServiceUnexpectedError)
      }

  }

  override def reScan(reportFileId: List[ReportFileId]): Future[Either[AntivirusServiceError, Unit]] = {

    val request = basicRequest
      .headers(Header("X-Api-Key", conf.antivirusApiKey))
      .post(
        uri"${conf.antivirusApiUrl}".withWholePath(ReScanEndPoint)
      )
      .body(reportFileId.map(_.value.toString))

    val response =
      request.send(backend)

    response
      .map { res =>
        res.code match {
          case StatusCode.NoContent || StatusCode.Ok =>
            logger.debug("Scan request successful: No content returned as expected.")
            Right(())
          case _ =>
            logger.warnWithTitle(
              "antivirus_scan_request_error",
              s"Unexpected response status for scan reportId  (${reportFileId.mkString(",")}): ${res.code}, body: ${res.body}"
            )
            Left(AntivirusScanRequestFailed)
        }
      }
      .recover { case error: Throwable =>
        logger.warnWithTitle(
          "antivirus_scan_request_error",
          s"Cannot send antivirus request for reportId :  (${reportFileId.mkString(",")})",
          error
        )
        Left(AntivirusServiceUnexpectedError)
      }

  }

  override def fileStatus(reportFileId: ReportFileId): Future[Either[AntivirusServiceError, FileData]] = {

    val uri = uri"${conf.antivirusApiUrl}/${ScanStatusEndPoint}/${reportFileId.toString}"

    val request = basicRequest
      .headers(Header("X-Api-Key", conf.antivirusApiKey))
      .post(
        uri
      )
      .response(asJson[FileData])

    val response =
      request.send(backend)
    response
      .map(_.body)
      .map {
        case Right(fileData) =>
          Right(fileData)
        case Left(error) =>
          logger.warnWithTitle(
            "antivirus_scan_status_request_error",
            s"Cannot get antivirus scan status for reportId :  $reportFileId",
            error
          )
          Left(AntivirusServiceFileStatusError)
      }

  }

  override def isActive: Boolean = conf.active
}

object AntivirusService {

  sealed trait AntivirusServiceError
  case object AntivirusScanRequestFailed      extends AntivirusServiceError
  case object AntivirusServiceUnexpectedError extends AntivirusServiceError
  case object AntivirusServiceFileStatusError extends AntivirusServiceError

}
