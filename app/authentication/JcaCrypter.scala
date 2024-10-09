package authentication

import authentication.JcaCrypter.UnderlyingIVBug
import authentication.JcaCrypter.UnexpectedFormat
import authentication.JcaCrypter.UnknownVersion
import controllers.error.AppError.BrokenAuthError

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Crypter implementation based on JCA (Java Cryptography Architecture).
  *
  * The algorithm used in this implementation is `AES/CTR/NoPadding`. Beware that CTR is
  * [[https://en.wikipedia.org/wiki/Malleability_%28cryptography%29 malleable]], which might be abused for various
  * attacks if messages are not properly
  * [[https://en.wikipedia.org/wiki/Malleability_%28cryptography%29 authenticated]].
  *
  * @param settings
  *   The settings instance.
  */
class JcaCrypter(settings: JcaCrypterSettings) {

  /** Encrypts a string.
    *
    * @param value
    *   The plain text to encrypt.
    * @return
    *   The encrypted string.
    */
  def encrypt(value: String): Either[BrokenAuthError, String] = {
    val keySpec = secretKeyWithSha256(settings.key, "AES")
    val cipher  = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    val encryptedValue = cipher.doFinal(value.getBytes("UTF-8"))
    val version        = 1
    Option(cipher.getIV) match {
      case Some(iv) => Right(s"$version-${Base64.getEncoder.encodeToString(iv ++ encryptedValue)}")
      case None     => Left(BrokenAuthError(UnderlyingIVBug))
    }
  }

  /** Decrypts a string.
    *
    * @param value
    *   The value to decrypt.
    * @return
    *   The plain text string.
    */
  def decrypt(value: String): Either[BrokenAuthError, String] =
    value.split("-", 2) match {
      case Array(version, data) if version == "1" => Right(decryptVersion1(data, settings.key))
      case Array(version, _)                      => Left(BrokenAuthError(UnknownVersion.format(version)))
      case _                                      => Left(BrokenAuthError(UnexpectedFormat))
    }

  /** Generates the SecretKeySpec, given the private key and the algorithm.
    */
  private def secretKeyWithSha256(privateKey: String, algorithm: String) = {
    val messageDigest = MessageDigest.getInstance("SHA-256")
    messageDigest.update(privateKey.getBytes("UTF-8"))
    // max allowed length in bits / (8 bits to a byte)
    val maxAllowedKeyLength = Cipher.getMaxAllowedKeyLength(algorithm) / 8
    val raw                 = messageDigest.digest().slice(0, maxAllowedKeyLength)
    new SecretKeySpec(raw, algorithm)
  }

  /** V1 decryption algorithm (AES/CTR/NoPadding - IV present).
    */
  private def decryptVersion1(value: String, privateKey: String): String = {
    val data      = Base64.getDecoder.decode(value)
    val keySpec   = secretKeyWithSha256(privateKey, "AES")
    val cipher    = Cipher.getInstance("AES/CTR/NoPadding")
    val blockSize = cipher.getBlockSize
    val iv        = data.slice(0, blockSize)
    val payload   = data.slice(blockSize, data.size)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv))
    new String(cipher.doFinal(payload), "UTF-8")
  }
}

object JcaCrypter {

  val UnderlyingIVBug = "[Silhouette][JcaCrypter] Cannot get IV! There must be a bug in your underlying JCE " +
    "implementation; The AES/CTR/NoPadding transformation should always provide an IV"
  val UnexpectedFormat = "[Silhouette][JcaCrypter] Unexpected format; expected [VERSION]-[ENCRYPTED STRING]"
  val UnknownVersion   = "[Silhouette][JcaCrypter] Unknown version: %s"
}

/** The settings for the JCA crypter.
  *
  * @param key
  *   The encryption key.
  */
case class JcaCrypterSettings(key: String)
