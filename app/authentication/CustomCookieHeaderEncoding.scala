package authentication

import authentication.CookieAuthenticator.CookieAuthenticatorSettings
import play.api.Logger
import play.api.mvc.Cookie.SameSite
import play.api.mvc.Cookie
import play.api.mvc.DefaultCookieHeaderEncoding
class CustomCookieHeaderEncoding(cookieSettings: CookieAuthenticatorSettings) extends DefaultCookieHeaderEncoding {

  val logger = Logger(this.getClass)
  override def encodeSetCookieHeader(cookies: Seq[Cookie]): String = {
    val setCookieHeader = super.encodeSetCookieHeader(cookies)
    if (setCookieHeader.contains(cookieSettings.cookieName) && cookieSettings.sameSite.contains(SameSite.None)) {
      // On demo, our api is on a different domain
      // The cookie is considered a third party cookie.
      // Thus we configure SameSite=None on demo
      // Chrome is now restricting 3rd party cookies even more : https://developers.google.com/privacy-sandbox/3pcd
      // Our solution : add the Partitioned attribute (see https://developers.google.com/privacy-sandbox/3pcd/chips)
      // Play doesn't support it so we have to hack around https://github.com/orgs/playframework/discussions/12357
      val sameSiteNone = s"SameSite=${SameSite.None.value};"
      setCookieHeader.replaceFirst(sameSiteNone, s"$sameSiteNone Partitioned;")
    } else setCookieHeader
  }
}
