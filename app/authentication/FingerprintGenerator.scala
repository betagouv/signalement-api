package authentication

import play.api.http.HeaderNames.ACCEPT_CHARSET
import play.api.http.HeaderNames.ACCEPT_LANGUAGE
import play.api.http.HeaderNames.USER_AGENT
import play.api.mvc.RequestHeader

import java.security.MessageDigest

/** A generator which creates a SHA1 fingerprint from `User-Agent`, `Accept-Language`, `Accept-Charset` and
  * `Accept-Encoding` headers and if defined the remote address of the user.
  *
  * The `Accept` header would also be a good candidate, but this header makes problems in applications which uses
  * content negotiation. So the default fingerprint generator doesn't include it.
  *
  * The same with `Accept-Encoding`. But in Chromium/Blink based browser the content of this header may be changed
  * during requests. @see https://github.com/mohiva/play-silhouette/issues/277
  *
  * @param includeRemoteAddress
  *   Indicates if the remote address should be included into the fingerprint.
  */
class FingerprintGenerator {

  private def sha1(str: String): String = sha1(str.getBytes("UTF-8"))

  private def sha1(bytes: Array[Byte]) =
    MessageDigest.getInstance("SHA-1").digest(bytes).map("%02x".format(_)).mkString

  /** Generates a fingerprint from request.
    *
    * @param request
    *   The request header.
    * @return
    *   The generated fingerprint.
    */
  def generate(request: RequestHeader): String =
    sha1(readHeadersValues(request).mkString(":"))

  def readHeadersValues(request: RequestHeader): Seq[String] =
    Seq(
      request.headers.get(USER_AGENT).getOrElse(""),
      request.headers.get(ACCEPT_LANGUAGE).getOrElse(""),
      request.headers.get(ACCEPT_CHARSET).getOrElse("")
    )
}
