package services

import com.itextpdf.html2pdf.{ConverterProperties, HtmlConverter}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import config.SignalConsoConfiguration
import org.xhtmlrenderer.pdf.ITextRenderer
import play.api.Logger
import play.twirl.api.HtmlFormat

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.concurrent.ExecutionContext

class PDFService(
    signalConsoConfiguration: SignalConsoConfiguration
) {

  val logger: Logger       = Logger(this.getClass)
  val tmpDirectory: String = signalConsoConfiguration.tmpDirectory

  def createPdfSource(
      htmlDocuments: Seq[HtmlFormat.Appendable]
  )(implicit ec: ExecutionContext): Source[ByteString, Unit] = {

//    val htmlStream = new ByteArrayInputStream(htmlDocuments.map(_.body).mkString.getBytes())
//
//    val pipedOutputStream = new PipedOutputStream()
//    val pipeSize          = 8192 // To match the pekko stream chunk size
//    val pipedInputStream  = new PipedInputStream(pipedOutputStream, pipeSize)

//    actor ! HtmlConverterActor.Convert(htmlStream, pipedOutputStream, converterProperties)


    val pdfOutputStream = new ByteArrayOutputStream
    println("--------------------------")
    println(htmlDocuments.map(_.body).mkString)
    println("--------------------------")

//    val renderer = new ITextRenderer()
//    renderer.setDocumentFromString(htmlDocuments.map(_.body).mkString, signalConsoConfiguration.apiURL.toString)
//    renderer.layout()
//    renderer.createPDF(pdfOutputStream)

    val converterProperties = new ConverterProperties()
//    val dfp                 = new DefaultFontProvider(false, true, true)
//    converterProperties.setFontProvider(dfp)
    converterProperties.setBaseUri(signalConsoConfiguration.apiURL.toString)
    val htmlStream = new ByteArrayInputStream(htmlDocuments.map(_.body).mkString.getBytes())
    HtmlConverter.convertToPdf(htmlStream, pdfOutputStream, converterProperties)


//    val pdfSource = StreamConverters
//      .fromInputStream(() => pipedInputStream)
//      .mapMaterializedValue(_.onComplete(_ => pipedInputStream.close()))

    val res = pdfOutputStream.toByteArray
    pdfOutputStream.close()

    Source.single(ByteString.fromArray(res)).mapMaterializedValue(_ => ())
//    pdfSource
  }

  def getPdfData(htmlDocument: HtmlFormat.Appendable): Array[Byte] = {
    val pdfOutputStream = new ByteArrayOutputStream

    val renderer = new ITextRenderer()
    renderer.setDocumentFromString(htmlDocument.body, signalConsoConfiguration.apiURL.toString)
    renderer.layout()
    renderer.createPDF(pdfOutputStream)

    pdfOutputStream.toByteArray
  }
}
