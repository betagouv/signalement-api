package services

import actors.HtmlConverterActor
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.util.ByteString
import config.SignalConsoConfiguration
import org.xhtmlrenderer.pdf.ITextRenderer
import play.api.Logger
import play.twirl.api.HtmlFormat

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

    val htmlBody = htmlDocuments.map(_.body).mkString

    val pipedOutputStream = new PipedOutputStream()
    val pipeSize          = 8192 // To match the pekko stream chunk size
    val pipedInputStream  = new PipedInputStream(pipedOutputStream, pipeSize)

    actor ! HtmlConverterActor.Convert(htmlBody, pipedOutputStream, signalConsoConfiguration.apiURL.toString)

    val pdfSource = StreamConverters
      .fromInputStream(() => pipedInputStream)
      .mapMaterializedValue(_.onComplete(_ => pipedInputStream.close()))

    pdfSource
  }

  def getPdfData(htmlDocument: HtmlFormat.Appendable): Array[Byte] = {
    val pdfOutputStream = new ByteArrayOutputStream

    val renderer = new ITextRenderer()
    renderer.setDocumentFromString(htmlDocument.body, signalConsoConfiguration.apiURL.toString)
    renderer.layout()
    renderer.createPDF(pdfOutputStream)
    renderer.finishPDF()

    pdfOutputStream.toByteArray
  }
}
