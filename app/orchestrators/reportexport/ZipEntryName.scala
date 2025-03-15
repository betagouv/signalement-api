package orchestrators.reportexport

import models.report.Report
import models.report.ReportFileApi
import orchestrators.ReportWithData

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

trait ZipEntryName {
  val value: String
}

object ZipEntryName {

  val pattern = DateTimeFormatter.ofPattern("dd-MM-yyyy HHmmss")

  private def safeString(stringToSanities: String) = {

    val notAllowedChars = List('\u0000', '\\', '/', ':', '*', '?', '"', '<', '>', '|', '.')

    stringToSanities.map { c =>
      if (notAllowedChars.contains(c)) {
        ""
      } else c

    }.mkString
  }

  case class AttachmentZipEntryName(value: String) extends ZipEntryName

  object AttachmentZipEntryName {
    def apply(
        reportName: Option[ReportZipEntryName],
        reportFile: ReportFileApi,
        creationDate: OffsetDateTime,
        index: Int
    ): AttachmentZipEntryName = {

      val formattedDate  = creationDate.format(pattern)
      val attachmentName = safeString(s"${formattedDate}-PJ-$index.${getFileExtension(reportFile.filename)}")
      new AttachmentZipEntryName(
        s"${reportName.map(_.directory).getOrElse("")}$attachmentName"
      )
    }

    private def getFileExtension(fileName: String): String =
      fileName.lastIndexOf(".") match {
        case -1 => "" // No extension found
        case i  => fileName.substring(i + 1)
      }

  }

  case class ReportZipEntryName(baseFileName: String, isSingleExport: Boolean) extends ZipEntryName {
    val value =
      if (isSingleExport) {
        s"$baseFileName/$baseFileName.pdf"
      } else {
        s"$baseFileName.pdf"
      }

    val directory = if (isSingleExport) s"$baseFileName/" else ""
  }

  object ReportZipEntryName {

    def apply(target: ReportWithData, isSingleExport: Boolean): ReportZipEntryName =
      reportFileName(target: ReportWithData, isSingleExport)

    private def reportFileName(target: ReportWithData, isSingleExport: Boolean): ReportZipEntryName =
      new ReportZipEntryName(reportName(target.report), isSingleExport)

    private def reportName(report: Report): String = {
      val date     = report.creationDate.format(pattern)
      val company  = companyName(report)
      val consumer = s"${report.firstName}_${report.lastName}"
      safeString(s"${date}_${company}_${consumer}")
    }

    private def companyName(report: Report): String = {

      val company = report.companySiret.map { siret =>
        report.companyName.orElse(report.companyBrand).orElse(report.companyBrand).getOrElse(siret.value)
      }
      val website = report.websiteURL.host.map(_.replaceAll("/.", "DOT"))

      val influencer = report.influencer.map(influencer =>
        s"${influencer.socialNetwork.map(slug => s"${slug.entryName}_").getOrElse("")}${influencer.name}"
      )

      val country = report.companyAddress.country.map(_.name)

      company.orElse(website).orElse(influencer).orElse(country).map(_.appended('_')) getOrElse ""
    }

  }
}
