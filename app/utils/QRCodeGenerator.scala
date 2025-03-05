package utils

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter

import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object QRCodeGenerator {
  def generate(text: String, width: Int, height: Int): Array[Byte] = {
    val qrCodeWriter = new QRCodeWriter()
    val bitMatrix    = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height)
    val image        = MatrixToImageWriter.toBufferedImage(bitMatrix)
    val baos         = new ByteArrayOutputStream()
    ImageIO.write(image, "png", baos)
    baos.toByteArray
  }
}
