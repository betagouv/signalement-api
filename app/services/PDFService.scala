package services

import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import config.SignalConsoConfiguration
import play.api.Logger
import play.twirl.api.HtmlFormat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import scala.concurrent.ExecutionContext

class PDFService(
    signalConsoConfiguration: SignalConsoConfiguration
) {

  val logger: Logger       = Logger(this.getClass)
  val tmpDirectory: String = signalConsoConfiguration.tmpDirectory

  def createPdfSource(
      _htmlDocuments: Seq[HtmlFormat.Appendable]
  )(implicit ec: ExecutionContext): Source[ByteString, Unit] = {
    val converterProperties = new ConverterProperties
    val dfp                 = new DefaultFontProvider(false, true, true)
    converterProperties.setFontProvider(dfp)
    converterProperties.setBaseUri(signalConsoConfiguration.apiURL.toString)

    val pipedOutputStream = new PipedOutputStream()
    val pipeSize          = 8192 // To match the akka stream chunk size
    val pipedInputStream  = new PipedInputStream(pipedOutputStream, pipeSize)

    val pdfSource = StreamConverters
      .fromInputStream(() => pipedInputStream)
      .mapMaterializedValue(_.onComplete(_ => pipedInputStream.close()))

    pdfSource
  }

  def getPdfData(htmlDocument: HtmlFormat.Appendable): Array[Byte] = {
    val converterProperties = new ConverterProperties
    val dfp                 = new DefaultFontProvider(true, true, true)
    converterProperties.setFontProvider(dfp)
    converterProperties.setBaseUri(signalConsoConfiguration.apiURL.toString)

    val pdfOutputStream = new ByteArrayOutputStream

    HtmlConverter.convertToPdf(
      new ByteArrayInputStream(htmlDocument.body.mkString.getBytes()),
      pdfOutputStream,
      converterProperties
    )
    pdfOutputStream.toByteArray
  }
}
