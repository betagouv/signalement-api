package services

import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import config.AppConfigLoader
import play.api.Logger
import play.api.http.FileMimeTypes
import play.twirl.api.HtmlFormat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class PDFService @Inject() (
    appConfigLoader: AppConfigLoader
) {

  val logger: Logger = Logger(this.getClass)
  val tmpDirectory = appConfigLoader.get.tmpDirectory

  def Ok(htmlDocuments: List[HtmlFormat.Appendable])(implicit ec: ExecutionContext, fmt: FileMimeTypes) = {
    val tmpFileName = s"${tmpDirectory}/${UUID.randomUUID}_${OffsetDateTime.now.toString}.pdf";
    val pdf = new PdfDocument(new PdfWriter(tmpFileName))

    val converterProperties = new ConverterProperties
    val dfp = new DefaultFontProvider(false, true, true)
    converterProperties.setFontProvider(dfp)
    converterProperties.setBaseUri(appConfigLoader.get.apiURL.toString)

    HtmlConverter.convertToPdf(
      new ByteArrayInputStream(htmlDocuments.map(_.body).mkString.getBytes()),
      pdf,
      converterProperties
    )
    logger.debug(f"Generated ${tmpFileName}")
    play.api.mvc.Results.Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
  }

  def getPdfData(htmlDocument: HtmlFormat.Appendable) = {
    val converterProperties = new ConverterProperties
    val dfp = new DefaultFontProvider(true, true, true)
    converterProperties.setFontProvider(dfp)
    converterProperties.setBaseUri(appConfigLoader.get.apiURL.toString())

    val pdfOutputStream = new ByteArrayOutputStream

    HtmlConverter.convertToPdf(
      new ByteArrayInputStream(htmlDocument.body.mkString.getBytes()),
      pdfOutputStream,
      converterProperties
    )
    pdfOutputStream.toByteArray
  }
}
