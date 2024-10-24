package tasks

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import config.TaskConfiguration
import models.barcode.BarcodeProduct
import models.company.Company
import models.report.Report
import repositories.report.ReportRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import tasks.model.TaskSettings.DailyTaskSettings

import java.io.File
import java.io.FileWriter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ExportReportsToSFTPTask(
    actorSystem: ActorSystem,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    reportRepository: ReportRepositoryInterface
)(implicit
    executionContext: ExecutionContext,
    materializer: Materializer
) extends ScheduledTask(7, "export_reports_to_sftp_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.exportReportsToSFTP.startTime)

  val batchSize = 10000
  val csvHeader = List(
    "id",
    "lang",
    "category",
    "subcategories",
    "host",
    "vendor",
    "tags",
    "details",
    "ccrf_codes",
    "creation_date",
    "status",
    "forward_to_reponse_conso",
    "reponseconso_code",
    "company_siret",
    "company_name",
    "company_brand",
    "company_activity_code",
    "company_country",
    "company_postal_code",
    "expiration_date",
    "company_is_head_office",
    "company_is_open",
    "gtin",
    "social_network",
    "other_social_network",
    "influenceur_name"
  ).mkString(",")

  private def wrapAndEscapeQuotes(s: String): String =
    s""""${s.replace("\"", "\"\"")}""""

  private def wrapAndEscapeQuotes(maybeString: Option[String]): String =
    maybeString match {
      case Some(s) => s""""${s.replace("\"", "\"\"")}""""
      case None    => ""
    }

  private def wrapAndEscapeQuotes(s: List[String]): String =
    s""""${s.mkString(";").replace("\"", "\"\"").take(3998)}"""" // Asked by SI

  private def printReport(
      report: Report,
      maybeCompany: Option[Company],
      maybeProduct: Option[BarcodeProduct]
  ): String = {
    import report._
    val fields: List[String] = List(
      id.toString,
      wrapAndEscapeQuotes(lang.map(_.toLanguageTag)),
      wrapAndEscapeQuotes(category),
      wrapAndEscapeQuotes(subcategories),
      wrapAndEscapeQuotes(websiteURL.host),
      wrapAndEscapeQuotes(vendor),
      wrapAndEscapeQuotes(tags.map(_.entryName)),
      wrapAndEscapeQuotes(details.map(input => s"${input.label}:${input.value}")),
      wrapAndEscapeQuotes(ccrfCode),
      creationDate.toString,
      status.entryName,
      forwardToReponseConso.toString,
      wrapAndEscapeQuotes(reponseconsoCode),
      companySiret.map(_.value).getOrElse(""),
      wrapAndEscapeQuotes(companyName),
      wrapAndEscapeQuotes(companyBrand),
      companyActivityCode.getOrElse(""),
      wrapAndEscapeQuotes(companyAddress.country.map(_.name)),
      wrapAndEscapeQuotes(companyAddress.postalCode),
      expirationDate.toString,
      maybeCompany.map(_.isHeadOffice.toString).getOrElse(""),
      maybeCompany.map(_.isOpen.toString).getOrElse(""),
      maybeProduct.map(_.gtin).getOrElse(""),
      wrapAndEscapeQuotes(influencer.flatMap(_.socialNetwork).map(_.entryName)),
      wrapAndEscapeQuotes(influencer.flatMap(_.otherSocialNetwork)),
      wrapAndEscapeQuotes(influencer.map(_.name))
    )

    fields.mkString(",")
  }

  override def runTask(): Future[Unit] = {
    logger.info("Streaming all reports as CSV to SFTP")
    val reports = reportRepository.streamAll
    logger.debug(s"Opening file to write ${taskConfiguration.exportReportsToSFTP.filePath}")
    val file = new File(taskConfiguration.exportReportsToSFTP.filePath)
    logger.info(s"Exporting data to ${file.getPath}")
    if (file.exists()) {
      logger.debug("File already exists, deleting.")
      file.delete()
    }
    val fileWriter = new FileWriter(file)

    fileWriter.write(csvHeader)
    fileWriter.write("\n")

    Source
      .fromPublisher(reports)
      .grouped(batchSize)
      .runForeach { reports =>
        val line = reports
          .map { case ((report, maybeCompany), maybeProduct) => printReport(report, maybeCompany, maybeProduct) }
          .mkString("\n")
        fileWriter.write(line)
        fileWriter.write("\n")
      }
      .map { _ =>
        logger.debug("Closing file.")
        fileWriter.flush()
        fileWriter.close()
      }
      .recover { e =>
        logger.warn("An error occurred while exporting reports to SFTP as CSV", e)
        fileWriter.close()
      }
  }
}
