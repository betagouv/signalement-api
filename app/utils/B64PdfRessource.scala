package utils

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64
import scala.io.BufferedSource

object B64PdfRessource {

  val ReportCss: String = {
    val source: BufferedSource = scala.io.Source.fromFile("css/report.css")
    val css                    = source.mkString
    source.close()
    css
  }

  val LogoMinister: String = {
    val imgBytes = Files.readAllBytes(Paths.get("images/logo-ministere.jpg"))
    val base64   = Base64.getEncoder.encodeToString(imgBytes)
    s"data:image/jpg;base64,$base64"
  }

  val LogoSignalConso: String = {
    val imgBytes = Files.readAllBytes(Paths.get("images/logo-signal-conso.png"))
    val base64   = Base64.getEncoder.encodeToString(imgBytes)
    s"data:image/png;base64,$base64"
  }

  val LogoMarianne: String = {
    val imgBytes = Files.readAllBytes(Paths.get("images/logo-marianne.jpg"))
    val base64   = Base64.getEncoder.encodeToString(imgBytes)
    s"data:image/jpg;base64,$base64"
  }

}
