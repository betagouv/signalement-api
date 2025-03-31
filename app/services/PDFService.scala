package services

import actors.HtmlConverterActor
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.util.ByteString
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
    signalConsoConfiguration: SignalConsoConfiguration,
    actor: ActorRef[HtmlConverterActor.ConvertCommand]
) {

  val logger: Logger       = Logger(this.getClass)
  val tmpDirectory: String = signalConsoConfiguration.tmpDirectory

  def createPdfSource(
      htmlDocuments: Seq[HtmlFormat.Appendable]
  )(implicit ec: ExecutionContext): Source[ByteString, Unit] = {
    val converterProperties = new ConverterProperties
    val dfp                 = new DefaultFontProvider(false, true, true)
    converterProperties.setFontProvider(dfp)
    converterProperties.setBaseUri(signalConsoConfiguration.apiURL.toString)

    val htmlStream = new ByteArrayInputStream(htmlDocuments.map(_.body).mkString.getBytes())

    val pipedOutputStream = new PipedOutputStream()
    val pipeSize          = 8192 // To match the pekko stream chunk size
    val pipedInputStream  = new PipedInputStream(pipedOutputStream, pipeSize)

    actor ! HtmlConverterActor.Convert(htmlStream, pipedOutputStream, converterProperties)

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
