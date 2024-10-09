package authentication

import authentication.JcaSigner.BadSignature
import authentication.JcaSigner.InvalidMessageFormat
import authentication.JcaSigner.UnknownVersion
import controllers.error.AppError.BrokenAuthError
import org.apache.commons.codec.binary.Hex

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Signer implementation based on JCA (Java Cryptography Architecture).
  *
  * This signer signs the data with the specified key. If the signature verification fails, the signer does not try to
  * decode the data in any way in order to prevent various types of attacks.
  *
  * @param settings
  *   The settings instance.
  */
class JcaSigner(settings: JcaSignerSettings) {

  /** Signs (MAC) the given data using the given secret key.
    *
    * @param data
    *   The data to sign.
    * @return
    *   A message authentication code.
    */
  def sign(data: String): String = {
    val message = settings.pepper + data + settings.pepper
    val mac     = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(settings.key.getBytes("UTF-8"), "HmacSHA1"))
    val signature = Hex.encodeHexString(mac.doFinal(message.getBytes("UTF-8")))
    val version   = 1
    s"$version-$signature-$data"
  }

  /** Extracts a message that was signed by the `sign` method.
    *
    * @param message
    *   The signed message to extract.
    * @return
    *   The verified raw data, or an error if the message isn't valid.
    */
  def extract(message: String): Either[BrokenAuthError, String] =
    for {
      tuple1 <- fragment(message)
      (_, actualSignature, actualData) = tuple1
      tuple2 <- fragment(sign(actualData))
      (_, expectedSignature, _) = tuple2
      res <-
        if (constantTimeEquals(expectedSignature, actualSignature)) {
          Right(actualData)
        } else {
          Left(BrokenAuthError(BadSignature))
        }
    } yield res

  /** Fragments the message into its parts.
    *
    * @param message
    *   The message to fragment.
    * @return
    *   The message parts.
    */
  private def fragment(message: String): Either[BrokenAuthError, (String, String, String)] =
    message.split("-", 3) match {
      case Array(version, signature, data) if version == "1" => Right((version, signature, data))
      case Array(version, _, _)                              => Left(BrokenAuthError(UnknownVersion.format(version)))
      case _                                                 => Left(BrokenAuthError(InvalidMessageFormat))
    }

  /** Constant time equals method.
    *
    * Given a length that both Strings are equal to, this method will always run in constant time. This prevents timing
    * attacks.
    */
  private def constantTimeEquals(a: String, b: String): Boolean =
    if (a.length != b.length) {
      false
    } else {
      var equal = 0
      for (i <- 0 until a.length)
        equal |= a(i) ^ b(i)
      equal == 0
    }
}

object JcaSigner {

  val BadSignature         = "[JcaSigner] Bad signature"
  val UnknownVersion       = "[JcaSigner] Unknown version: %s"
  val InvalidMessageFormat = "[JcaSigner] Invalid message format; Expected [VERSION]-[SIGNATURE]-[DATA]"
}

/** The settings for the JCA signer.
  *
  * @param key
  *   Key for signing.
  * @param pepper
  *   Constant prepended and appended to the data before signing. When using one key for multiple purposes, using a
  *   specific pepper reduces some risks arising from this.
  */
case class JcaSignerSettings(key: String, pepper: String = "-pepper-signer-")
