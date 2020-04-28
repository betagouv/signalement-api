package utils

import java.util.UUID
import java.time.OffsetDateTime

import java.io.{ByteArrayInputStream, File}
import scala.concurrent.ExecutionContext
import play.twirl.api.HtmlFormat
import play.api.http.FileMimeTypes
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import com.itextpdf.html2pdf.{ConverterProperties, HtmlConverter}
import com.itextpdf.kernel.pdf.{PdfDocument, PdfWriter}


object PDF {
    def Ok(htmlDocuments: List[HtmlFormat.Appendable], tmpDirectory: String, websiteUrl: String)
        (implicit ec: ExecutionContext, fmt: FileMimeTypes) = {
        val tmpFileName = s"${tmpDirectory}/${UUID.randomUUID}_${OffsetDateTime.now.toString}.pdf";
        val pdf = new PdfDocument(new PdfWriter(tmpFileName))

        val converterProperties = new ConverterProperties
        val dfp = new DefaultFontProvider(true, true, true)
        converterProperties.setFontProvider(dfp)
        converterProperties.setBaseUri(websiteUrl)

        HtmlConverter.convertToPdf(new ByteArrayInputStream(htmlDocuments.map(_.body).mkString.getBytes()), pdf, converterProperties)

        play.api.mvc.Results.Ok.sendFile(new File(tmpFileName), onClose = () => new File(tmpFileName).delete)
    }
}